package dev.caden.mccitweaks

import com.fishingmod.FishingModClient
import com.fishingmod.FishingModConfig
import com.fishingmod.FishingModConfigStore
import dev.caden.mccitweaks.chat.ChatConfigSection
import dev.caden.mccitweaks.chat.ChatSeparator
import dev.caden.mccitweaks.materialmarket.MaterialMarketConfigSection
import dev.caden.mccitweaks.ui.ConfigList
import dev.caden.mccitweaks.ui.CycleWidget
import dev.caden.mccitweaks.ui.ToggleWidget
import dev.caden.mccnoflash.FlashMode
import dev.caden.mccnoflash.MccNoFlashState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.tabs.GridLayoutTab
import net.minecraft.client.gui.components.tabs.MenuTabBar
import net.minecraft.client.gui.components.tabs.Tab
import net.minecraft.client.gui.components.tabs.TabManager
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class McciTweaksConfigScreen(private val parent: Screen?) : Screen(Component.literal("MCCI Tweaks")) {
    private val config = McciTweaksConfigStore.get().apply { applyDefaults() }
    private val automation = config.automation
    private val noFlash = MccNoFlashState.get().config().apply { ensureDefaults() }
    private val materialMarket = config.materialMarket
    private val chat = config.chat

    // Edits are applied to the live config objects; Cancel restores these copies.
    private val automationSnapshot = automation.copy()
    private val noFlashSnapshot = noFlash.copy()
    private val materialMarketSnapshot = materialMarket.copy()
    private val chatSnapshot = chat.copy()

    private val tabs = ArrayList<Tab>()
    private val tabManager = TabManager(
        { widget -> addRenderableWidget(widget) },
        { widget -> removeWidget(widget) },
        // Minecraft passes null for the previous tab the first time one is selected.
        { tab: Tab? -> onTabSelected(tab) },
        { _: Tab? -> }
    )

    private var screenLayout = HeaderAndFooterLayout(this)
    private var tabNavigationBar: MenuTabBar? = null
    private var selectedTab = 0

    override fun init() {
        screenLayout = HeaderAndFooterLayout(this)
        tabs.clear()
        tabs += ConfigTab(Component.literal("Fishtance"), buildAutomationList())
        tabs += ConfigTab(Component.literal("No Flash"), buildNoFlashList())
        tabs += ConfigTab(Component.literal("Material Market"), buildMaterialMarketList())
        tabs += ConfigTab(Component.literal("Chat"), buildChatList())

        val tabBar = MenuTabBar.builder(tabManager, width)
            .addTabs(*tabs.toTypedArray())
            .build()
        tabNavigationBar = tabBar
        addRenderableWidget(tabBar)

        val footer = screenLayout.addToFooter(LinearLayout.horizontal().spacing(8))
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL) { discardAndClose() }.width(150).build())
        footer.addChild(Button.builder(CommonComponents.GUI_DONE) { saveAndClose() }.width(150).build())

        screenLayout.visitWidgets { widget ->
            widget.setTabOrderGroup(1)
            addRenderableWidget(widget)
        }

        tabBar.selectTab(selectedTab, false)
        repositionElements()
    }

    private fun onTabSelected(tab: Tab?) {
        if (tab == null) return
        val index = tabs.indexOf(tab)
        if (index >= 0) {
            selectedTab = index
        }
    }

    override fun repositionElements() {
        val tabBar = tabNavigationBar ?: return

        tabBar.arrangeElements(width)
        val contentTop = tabBar.rectangle.bottom()
        screenLayout.setHeaderHeight(contentTop)
        screenLayout.arrangeElements()

        val tabArea = ScreenRectangle(0, contentTop, width, height - screenLayout.footerHeight - contentTop)
        tabBar.tabs.forEach { tab -> tab.visitChildren { child -> child.setHeight(tabArea.height()) } }
        tabManager.setTabArea(tabArea)
    }

    override fun tick() {
        (tabManager.currentTab as? ConfigTab)?.list?.tick()
    }

    override fun keyPressed(event: KeyEvent): Boolean =
        tabNavigationBar?.keyPressed(event) == true || super.keyPressed(event)

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            Screen.FOOTER_SEPARATOR,
            0,
            height - screenLayout.footerHeight,
            0.0f,
            0.0f,
            width,
            2,
            32,
            2
        )
    }

    override fun extractMenuBackground(graphics: GuiGraphicsExtractor) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            CreateWorldScreen.TAB_HEADER_BACKGROUND,
            0,
            0,
            0.0f,
            0.0f,
            width,
            screenLayout.headerHeight,
            16,
            16
        )
        extractMenuBackground(graphics, 0, screenLayout.headerHeight, width, height)
    }

    private fun newList(): ConfigList =
        ConfigList(minecraft, width, screenLayout.contentHeight, screenLayout.headerHeight)

    private fun buildAutomationList(): ConfigList = newList().apply {
        beginSection(Component.literal("Fishtance Automation"))
        addOption(
            ToggleWidget(
                Component.literal("Teleport Automation"),
                { automation.teleportEnabled == true },
                { automation.teleportEnabled = it },
                true
            )
        )
        addOption(
            CycleWidget(
                Component.literal("Target Fishtance Instance"),
                (FishingModConfig.MIN_TARGET_INSTANCE..FishingModConfig.MAX_TARGET_INSTANCE).toList(),
                { automation.targetInstance() },
                { automation.selectTargetInstance(it) },
                { Component.literal(it.toString()) },
                FishingModConfig.MAX_TARGET_INSTANCE
            )
        )
        addOption(
            ToggleWidget(Component.literal("Debug Logging"), { automation.debugLogging }, { automation.debugLogging = it }, false)
        )
        refresh()
    }

    private fun buildNoFlashList(): ConfigList = newList().apply {
        beginSection(Component.literal("No Flash"))
        addOption(ToggleWidget(Component.literal("Enabled"), { noFlash.enabled }, { noFlash.enabled = it }, true))
        addOption(flashMode("Global Flash Mode", { noFlash.globalMode }, { noFlash.globalMode = it }))
        addOption(
            ToggleWidget(Component.literal("Debug Logging"), { noFlash.debugLogging }, { noFlash.debugLogging = it }, false)
        )

        beginSection(Component.literal("Per-Menu Overrides"))
        addOption(flashMode("Journal", { noFlash.journalMode }, { noFlash.journalMode = it }))
        addOption(flashMode("Pocket Menu", { noFlash.pocketMode }, { noFlash.pocketMode = it }))
        addOption(flashMode("Navigator", { noFlash.navigatorMode }, { noFlash.navigatorMode = it }))
        addOption(flashMode("A.N.G.L.R Panel", { noFlash.anglrPanelMode }, { noFlash.anglrPanelMode = it }))
        addOption(flashMode("My Profile", { noFlash.profileMode }, { noFlash.profileMode = it }))
        refresh()
    }

    private fun flashMode(label: String, getter: () -> FlashMode, setter: (FlashMode) -> Unit): CycleWidget<FlashMode> =
        CycleWidget(Component.literal(label), FlashMode.entries, getter, setter, { it.displayName }, FlashMode.DEFAULT)

    private fun buildMaterialMarketList(): ConfigList = newList().apply {
        MaterialMarketConfigSection.addTo(this, materialMarket, font)
        refresh()
    }

    private fun buildChatList(): ConfigList = newList().apply {
        ChatConfigSection.addTo(this, chat)
        refresh()
    }

    private fun saveAndClose() {
        materialMarket.apiKey = materialMarket.apiKey.trim()
        automation.applyDefaults()
        noFlash.ensureDefaults()
        materialMarket.applyDefaults()
        chat.applyDefaults()

        config.automation = automation
        config.noFlash = noFlash
        config.materialMarket = materialMarket
        config.chat = chat
        McciTweaksConfigStore.save(config)
        FishingModConfigStore.saveConfig(automation)
        FishingModClient.reloadConfigFromDisk()
        ChatSeparator.refreshChat()

        close()
    }

    private fun discardAndClose() {
        automation.copyFrom(automationSnapshot)
        noFlash.copyFrom(noFlashSnapshot)
        materialMarket.copyFrom(materialMarketSnapshot)
        chat.copyFrom(chatSnapshot)
        MaterialMarketConfigSection.notifyApiKeyChanged()
        ChatSeparator.refreshChat()
        close()
    }

    private fun close() {
        minecraft?.gui?.setScreen(parent)
    }

    override fun onClose() {
        saveAndClose()
    }

    private inner class ConfigTab(title: Component, val list: ConfigList) : GridLayoutTab(title) {
        init {
            layout.addChild(list, 1, 1)
        }

        override fun doLayout(screenRectangle: ScreenRectangle) {
            list.updateSizeAndPosition(
                this@McciTweaksConfigScreen.width,
                screenLayout.contentHeight,
                screenLayout.headerHeight
            )
            super.doLayout(screenRectangle)
        }
    }
}
