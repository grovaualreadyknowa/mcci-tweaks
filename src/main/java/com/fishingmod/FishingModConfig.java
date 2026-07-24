package com.fishingmod;

import java.util.ArrayList;
import java.util.List;

public final class FishingModConfig {
    public static final int MIN_TARGET_INSTANCE = 8;
    public static final int MAX_TARGET_INSTANCE = 11;

    public boolean enabled = true;
    public boolean debugLogging = false;
    public Boolean teleportEnabled = Boolean.TRUE;
    public String targetServerHost = "";
    public String instanceCommand = "instance join 11";
    public Integer targetInstance;
    public int postTeleportDelayMs = 1500;
    public int travelTimeoutMs = 10000;
    public int scanCooldownMs = 250;
    public int commandCooldownMs = 30000;
    public List<String> navigatorKeywords = new ArrayList<>();
    public List<String> fishingDestinationKeywords = new ArrayList<>();
    public List<String> currentInstanceKeywords = new ArrayList<>();

    public static FishingModConfig defaults() {
        FishingModConfig config = new FishingModConfig();
        config.navigatorKeywords.add("navigator");
        config.navigatorKeywords.add("teleport");
        config.navigatorKeywords.add("warp");

        config.fishingDestinationKeywords.add("fishing");
        config.fishingDestinationKeywords.add("fish");
        config.fishingDestinationKeywords.add("angler");
        config.fishingDestinationKeywords.add("dock");
        config.fishingDestinationKeywords.add("harbor");
        config.fishingDestinationKeywords.add("ocean");
        config.fishingDestinationKeywords.add("sea");
        config.fishingDestinationKeywords.add("island");

        config.targetInstance = MAX_TARGET_INSTANCE;
        config.currentInstanceKeywords = config.keywordsForTargetInstance();
        return config;
    }

    public void applyDefaults() {
        if (navigatorKeywords == null || navigatorKeywords.isEmpty()) {
            navigatorKeywords = defaults().navigatorKeywords;
        }
        if (fishingDestinationKeywords == null || fishingDestinationKeywords.isEmpty()) {
            fishingDestinationKeywords = defaults().fishingDestinationKeywords;
        }

        if (targetInstance == null || !isValidTargetInstance(targetInstance)) {
            targetInstance = parseTargetInstance(instanceCommand);
        }
        instanceCommand = commandForTargetInstance();
        currentInstanceKeywords = keywordsForTargetInstance();
        if (postTeleportDelayMs <= 0) {
            postTeleportDelayMs = 1500;
        }
        if (travelTimeoutMs <= 0) {
            travelTimeoutMs = 10000;
        }
        if (scanCooldownMs <= 0) {
            scanCooldownMs = 250;
        }
        if (commandCooldownMs <= 0) {
            commandCooldownMs = 30000;
        }
        if (targetServerHost == null) {
            targetServerHost = "";
        }
        if (teleportEnabled == null) {
            teleportEnabled = Boolean.TRUE;
        }
    }


    public int targetInstance() {
        return targetInstance == null || !isValidTargetInstance(targetInstance)
                ? MAX_TARGET_INSTANCE
                : targetInstance;
    }

    public void cycleTargetInstance() {
        int next = targetInstance() + 1;
        targetInstance = next > MAX_TARGET_INSTANCE ? MIN_TARGET_INSTANCE : next;
        instanceCommand = commandForTargetInstance();
        currentInstanceKeywords = keywordsForTargetInstance();
    }

    public String commandForTargetInstance() {
        return "instance join " + targetInstance();
    }

    public List<String> keywordsForTargetInstance() {
        String instance = Integer.toString(targetInstance());
        List<String> keywords = new ArrayList<>();
        keywords.add("instance " + instance);
        keywords.add("inst " + instance);
        keywords.add("instance: " + instance);
        keywords.add("inst: " + instance);
        keywords.add("fishtance " + instance);
        keywords.add("fishtance: " + instance);
        keywords.add("fishtance #" + instance);
        keywords.add("fishtance-" + instance);
        keywords.add("fishstance " + instance);
        keywords.add("fishstance: " + instance);
        keywords.add("fishstance #" + instance);
        keywords.add("fishstance-" + instance);
        keywords.add("#" + instance);
        return keywords;
    }

    private static boolean isValidTargetInstance(int instance) {
        return instance >= MIN_TARGET_INSTANCE && instance <= MAX_TARGET_INSTANCE;
    }

    private static int parseTargetInstance(String command) {
        if (command != null) {
            String normalized = command.trim();
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            for (int instance = MIN_TARGET_INSTANCE; instance <= MAX_TARGET_INSTANCE; instance++) {
                if (normalized.equals("instance join " + instance)) {
                    return instance;
                }
            }
        }
        return MAX_TARGET_INSTANCE;
    }
}
