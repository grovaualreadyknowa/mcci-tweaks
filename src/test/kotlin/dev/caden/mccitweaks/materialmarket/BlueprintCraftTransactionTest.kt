package dev.caden.mccitweaks.materialmarket

import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlueprintCraftTransactionTest {
    @Test
    fun `parses owned and required blueprint amounts including commas`() {
        val materials = MaterialMarketInventory.parseBlueprintMaterialLines(
            listOf(
                Component.literal("[Seaweed Goop] [1,440/2,000]"),
                Component.literal("[Legendary Power Shard] [1/1]")
            )
        )

        assertEquals(2, materials.size)
        assertEquals("seaweed goop", materials[0].key)
        assertEquals(1440L, materials[0].owned)
        assertEquals(2000L, materials[0].required)
        assertEquals(1L, materials[1].owned)
        assertEquals(1L, materials[1].required)
    }

    @Test
    fun `incomplete blueprint material is not affordable`() {
        assertFalse(BlueprintCraftTransaction.Material("seaweed goop", "Seaweed Goop", 144L, 200L).isAffordable())
    }

    @Test
    fun `applies selection only after matching confirmed container update`() {
        val transaction = BlueprintCraftTransaction()
        val materials = listOf(BlueprintCraftTransaction.Material("deep brine", "Deep Brine", 101L, 12L))

        transaction.select(materials, 8)
        assertTrue(transaction.acknowledgeContainerUpdate(8).isEmpty())
        assertTrue(transaction.confirm(8))
        assertTrue(transaction.acknowledgeContainerUpdate(9).isEmpty())
        assertEquals(materials, transaction.acknowledgeContainerUpdate(8))
        assertTrue(transaction.acknowledgeContainerUpdate(8).isEmpty())
    }

    @Test
    fun `cancel prevents any later deduction`() {
        val transaction = BlueprintCraftTransaction()
        transaction.select(listOf(BlueprintCraftTransaction.Material("void", "Void", 21L, 7L)), 8)

        transaction.cancel()

        assertFalse(transaction.confirm(8))
        assertTrue(transaction.acknowledgeContainerUpdate(8).isEmpty())
    }

    @Test
    fun `dialog confirmation returns the selection immediately`() {
        val transaction = BlueprintCraftTransaction()
        val materials = listOf(BlueprintCraftTransaction.Material("deep brine", "Deep Brine", 101L, 12L))

        transaction.select(materials, 8)

        assertEquals(materials, transaction.confirmDialog())
        assertFalse(transaction.hasSelection())
        assertTrue(transaction.confirmDialog().isEmpty())
        assertTrue(transaction.acknowledgeContainerUpdate(8).isEmpty())
    }

    @Test
    fun `recognizes the labeled blueprint confirmation buttons`() {
        assertEquals(MaterialMarketInventory.BlueprintConfirmationAction.CONFIRM, MaterialMarketInventory.blueprintDialogButtonAction("Confirm"))
        assertEquals(MaterialMarketInventory.BlueprintConfirmationAction.CANCEL, MaterialMarketInventory.blueprintDialogButtonAction("Cancel"))
        assertEquals(MaterialMarketInventory.BlueprintConfirmationAction.NONE, MaterialMarketInventory.blueprintDialogButtonAction("Not this dialog"))
    }

    @Test
    fun `recognizes the server driven blueprint confirmation slots`() {
        val title = "ASSEMBLE THIS BLUEPRINT?"

        assertEquals(MaterialMarketInventory.BlueprintConfirmationAction.CONFIRM, MaterialMarketInventory.blueprintContainerAction(title, 48))
        assertEquals(MaterialMarketInventory.BlueprintConfirmationAction.CANCEL, MaterialMarketInventory.blueprintContainerAction(title, 50))
        assertEquals(MaterialMarketInventory.BlueprintConfirmationAction.NONE, MaterialMarketInventory.blueprintContainerAction(title, 49))
        assertEquals(
            MaterialMarketInventory.BlueprintConfirmationAction.NONE,
            MaterialMarketInventory.blueprintContainerAction("Blueprint Assembler", 48)
        )
    }
}
