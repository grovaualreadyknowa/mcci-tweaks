package dev.caden.mccnoflash;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.caden.mccitweaks.McciTweaksConfigScreen;
import dev.caden.mccitweaks.McciTweaksMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class MccNoFlashClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!BuildFlags.COMMANDS_ENABLED) {
            McciTweaksMod.LOGGER.info("Client commands disabled by build flag.");
            return;
        }
        registerCommands();
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildCommand("mccitweaks", "mccitweaks"));
            dispatcher.register(buildCommand("mccnoflash", "mccnoflash"));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String command, String label) {
        return ClientCommands.literal(command)
            .executes(context -> printStatus(context.getSource(), label))
            .then(ClientCommands.literal("config").executes(context -> openConfig(context.getSource())))
            .then(ClientCommands.literal("status").executes(context -> printStatus(context.getSource(), label)))
            .then(ClientCommands.literal("capture").executes(context -> printCapture(context.getSource(), label)))
            .then(ClientCommands.literal("explain").executes(context -> printDecision(context.getSource(), label)))
            .then(
                ClientCommands.literal("debug")
                    .then(ClientCommands.literal("on").executes(context -> {
                        MccNoFlashState.get().setDebugSession(true);
                        context.getSource().sendFeedback(Component.literal("[" + label + "] debug session enabled"));
                        return 1;
                    }))
                    .then(ClientCommands.literal("off").executes(context -> {
                        MccNoFlashState.get().setDebugSession(false);
                        context.getSource().sendFeedback(Component.literal("[" + label + "] debug session disabled"));
                        return 1;
                    }))
                    .then(ClientCommands.literal("toggle").executes(context -> {
                        MccNoFlashState state = MccNoFlashState.get();
                        state.setDebugSession(!state.isDebugSessionEnabled());
                        context.getSource().sendFeedback(Component.literal(
                            "[" + label + "] debug session=" + state.isDebugSessionEnabled()
                        ));
                        return 1;
                    }))
            )
            .then(ClientCommands.literal("scanpack").executes(context -> scanLatestPack(context.getSource(), label)));
    }

    private static int openConfig(FabricClientCommandSource source) {
        source.getClient().setScreen(new McciTweaksConfigScreen(source.getClient().screen));
        return 1;
    }

    private static int printStatus(FabricClientCommandSource source, String label) {
        MccNoFlashState state = MccNoFlashState.get();
        MccNoFlashConfig config = state.config();
        boolean onMcc = state.isOnMccServer(source.getClient());

        source.sendFeedback(Component.literal("[" + label + "] enabled=" + config.enabled
            + " debugConfig=" + config.debugLogging
            + " globalMode=" + config.globalMode
            + " journalMode=" + config.journalMode
            + " pocketMode=" + config.pocketMode
            + " navigatorMode=" + config.navigatorMode
            + " anglrPanelMode=" + config.anglrPanelMode
            + " profileMode=" + config.profileMode
            + " debugSession=" + state.isDebugSessionEnabled()
            + " onMcc=" + onMcc));
        source.sendFeedback(Component.literal("[" + label + "] decision " + state.getDecisionSummary()));
        return 1;
    }

    private static int printCapture(FabricClientCommandSource source, String label) {
        source.sendFeedback(Component.literal("[" + label + "] " + MccNoFlashState.get().getCaptureSummary()));
        return 1;
    }

    private static int printDecision(FabricClientCommandSource source, String label) {
        source.sendFeedback(Component.literal("[" + label + "] " + MccNoFlashState.get().getDecisionSummary()));
        return 1;
    }

    private static int scanLatestPack(FabricClientCommandSource source, String label) {
        MccNoFlashState state = MccNoFlashState.get();
        ServerPackScanner.ScanResult result = ServerPackScanner.scanLatest(state);

        source.sendFeedback(Component.literal("[" + label + "] " + result.summary()));
        source.sendFeedback(Component.literal(
            "[" + label + "] search keywords=" + String.join(", ", state.discoveryKeywords())
        ));

        if (!result.ok()) {
            source.sendFeedback(Component.literal("[" + label + "] dir=" + result.packDirectory()));
            return 1;
        }

        source.sendFeedback(Component.literal("[" + label + "] zip=" + result.scannedZip()));
        source.sendFeedback(Component.literal("[" + label + "] modified=" + result.lastModified()));

        List<String> matches = result.matches();
        if (matches.isEmpty()) {
            source.sendFeedback(Component.literal("[" + label + "] no keyword matches found"));
            return 1;
        }

        for (String match : matches) {
            source.sendFeedback(Component.literal("[" + label + "] match: " + match));
        }
        return 1;
    }
}
