package dev.caden.mccnoflash;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fabricmc.loader.api.FabricLoader;

public final class ServerPackScanner {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private ServerPackScanner() {
    }

    public static ScanResult scanLatest(MccNoFlashState state) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path serverPackDir = gameDir.resolve("server-resource-packs");
        Path downloadsDir = gameDir.resolve("downloads");

        Optional<PackCandidate> latestPack = findLatestPack(serverPackDir, downloadsDir);
        if (latestPack.isEmpty()) {
            return ScanResult.noPackFound(gameDir);
        }

        PackCandidate candidate = latestPack.get();
        Path zipPath = candidate.path();
        Set<String> keywords = state.discoveryKeywords();
        List<String> matches = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String lower = entry.getName().toLowerCase(Locale.ROOT);
                if (containsAny(lower, keywords)) {
                    matches.add(entry.getName());
                }
            }
        } catch (IOException e) {
            return ScanResult.error(candidate.searchRoot(), zipPath, e.getMessage());
        }

        List<String> ordered = state.sortByRelevance(matches);
        if (ordered.size() > 30) {
            ordered = ordered.subList(0, 30);
        }

        return ScanResult.success(candidate.searchRoot(), zipPath, ordered, readTime(zipPath));
    }

    private static Optional<PackCandidate> findLatestPack(Path serverPackDir, Path downloadsDir) {
        List<PackCandidate> candidates = new ArrayList<>();

        candidates.addAll(findCandidatesInServerPackDir(serverPackDir));
        candidates.addAll(findCandidatesInDownloadsDir(downloadsDir));

        return candidates.stream().max(Comparator.comparingLong(candidate -> lastModifiedMillis(candidate.path())));
    }

    private static List<PackCandidate> findCandidatesInServerPackDir(Path serverPackDir) {
        List<PackCandidate> candidates = new ArrayList<>();
        if (!Files.isDirectory(serverPackDir)) {
            return candidates;
        }

        try (var stream = Files.list(serverPackDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(ServerPackScanner::isLikelyZipFile)
                .forEach(path -> candidates.add(new PackCandidate(serverPackDir, path)));
        } catch (IOException e) {
            MccNoFlashMod.LOGGER.warn("Could not list server packs in {}", serverPackDir, e);
        }

        return candidates;
    }

    private static List<PackCandidate> findCandidatesInDownloadsDir(Path downloadsDir) {
        List<PackCandidate> candidates = new ArrayList<>();
        if (!Files.isDirectory(downloadsDir)) {
            return candidates;
        }

        try (var stream = Files.walk(downloadsDir, 4)) {
            stream
                .filter(Files::isRegularFile)
                .filter(ServerPackScanner::isLikelyZipFile)
                .forEach(path -> candidates.add(new PackCandidate(downloadsDir, path)));
        } catch (IOException e) {
            MccNoFlashMod.LOGGER.warn("Could not walk downloads dir {}", downloadsDir, e);
        }

        return candidates;
    }

    private static boolean isLikelyZipFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            return true;
        }

        byte[] header = new byte[4];
        try (InputStream input = Files.newInputStream(path)) {
            int read = input.read(header);
            if (read < 4) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        return header[0] == 0x50
            && header[1] == 0x4B
            && (header[2] == 0x03 || header[2] == 0x05 || header[2] == 0x07)
            && (header[3] == 0x04 || header[3] == 0x06 || header[3] == 0x08);
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private static String readTime(Path path) {
        try {
            FileTime ft = Files.getLastModifiedTime(path);
            return TS_FORMAT.format(Instant.ofEpochMilli(ft.toMillis()));
        } catch (IOException e) {
            return "unknown";
        }
    }

    private static boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (!keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    public record ScanResult(
        boolean ok,
        Path packDirectory,
        Path scannedZip,
        String lastModified,
        String failure,
        List<String> matches
    ) {
        public static ScanResult noPackFound(Path gameDir) {
            return new ScanResult(
                false,
                gameDir,
                null,
                null,
                "no server pack zip found in server-resource-packs or downloads",
                List.of()
            );
        }

        public static ScanResult error(Path packDirectory, Path scannedZip, String failure) {
            return new ScanResult(false, packDirectory, scannedZip, null, failure, List.of());
        }

        public static ScanResult success(Path packDirectory, Path scannedZip, List<String> matches, String lastModified) {
            return new ScanResult(true, packDirectory, scannedZip, lastModified, null, matches);
        }

        public String summary() {
            if (!ok) {
                return "scan failed: " + failure;
            }

            return "scan ok: " + matches.size() + " candidate files";
        }
    }

    private record PackCandidate(Path searchRoot, Path path) {
    }
}
