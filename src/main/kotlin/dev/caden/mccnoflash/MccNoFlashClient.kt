package dev.caden.mccnoflash

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.caden.mccitweaks.McciTweaksConfigScreen
import dev.caden.mccitweaks.McciTweaksMod
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

class MccNoFlashClient : ClientModInitializer {
    override fun onInitializeClient() {
        if (!BuildFlags.COMMANDS_ENABLED) {
            McciTweaksMod.LOGGER.info("Client commands disabled by build flag.")
            return
        }
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
            dispatcher.register(buildCommand("mccitweaks"))
            dispatcher.register(buildCommand("mccnoflash"))
        })
    }

    private fun buildCommand(label: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(label)
            .executes { printStatus(it.source, label) }
            .then(ClientCommands.literal("config").executes { openConfig(it.source) })
            .then(ClientCommands.literal("status").executes { printStatus(it.source, label) })
            .then(ClientCommands.literal("capture").executes {
                it.source.feedback(label, MccNoFlashState.get().getCaptureSummary())
            })
            .then(ClientCommands.literal("explain").executes {
                it.source.feedback(label, MccNoFlashState.get().getDecisionSummary())
            })
            .then(
                ClientCommands.literal("debug")
                    .then(ClientCommands.literal("on").executes {
                        MccNoFlashState.get().setDebugSession(true)
                        it.source.feedback(label, "debug session enabled")
                    })
                    .then(ClientCommands.literal("off").executes {
                        MccNoFlashState.get().setDebugSession(false)
                        it.source.feedback(label, "debug session disabled")
                    })
                    .then(ClientCommands.literal("toggle").executes {
                        val state = MccNoFlashState.get()
                        state.setDebugSession(!state.isDebugSessionEnabled())
                        it.source.feedback(label, "debug session=${state.isDebugSessionEnabled()}")
                    })
            )
            .then(ClientCommands.literal("scanpack").executes { scanLatestPack(it.source, label) })

    private fun FabricClientCommandSource.feedback(label: String, message: String): Int {
        sendFeedback(Component.literal("[$label] $message"))
        return 1
    }

    private fun openConfig(source: FabricClientCommandSource): Int {
        val gui = source.client.gui
        gui.setScreen(McciTweaksConfigScreen(gui.screen()))
        return 1
    }

    private fun printStatus(source: FabricClientCommandSource, label: String): Int {
        val state = MccNoFlashState.get()
        val config = state.config()
        source.feedback(
            label,
            "enabled=${config.enabled}" +
                " debugConfig=${config.debugLogging}" +
                " globalMode=${config.globalMode}" +
                " journalMode=${config.journalMode}" +
                " pocketMode=${config.pocketMode}" +
                " navigatorMode=${config.navigatorMode}" +
                " anglrPanelMode=${config.anglrPanelMode}" +
                " profileMode=${config.profileMode}" +
                " debugSession=${state.isDebugSessionEnabled()}" +
                " onMcc=${state.isOnMccServer(source.client)}"
        )
        return source.feedback(label, "decision ${state.getDecisionSummary()}")
    }

    private fun scanLatestPack(source: FabricClientCommandSource, label: String): Int {
        val state = MccNoFlashState.get()
        val result = ServerPackScanner.scanLatest(state)

        source.feedback(label, result.summary())
        source.feedback(label, "search keywords=${state.discoveryKeywords().joinToString(", ")}")

        if (!result.ok) {
            return source.feedback(label, "dir=${result.packDirectory}")
        }

        source.feedback(label, "zip=${result.scannedZip}")
        source.feedback(label, "modified=${result.lastModified}")

        if (result.matches.isEmpty()) {
            return source.feedback(label, "no keyword matches found")
        }
        result.matches.forEach { match -> source.feedback(label, "match: $match") }
        return 1
    }
}
