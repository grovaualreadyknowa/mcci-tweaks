package dev.caden.mccitweaks.materialmarket

/** Holds one Blueprint Assembler choice until the server acknowledges its confirmation. */
internal class BlueprintCraftTransaction {
    private var selectedMaterials: List<Material> = emptyList()
    private var selectedContainerId = -1
    private var confirmationContainerId = -1

    fun select(materials: List<Material>, containerId: Int) {
        selectedMaterials = materials.toList()
        selectedContainerId = containerId
        confirmationContainerId = -1
    }

    fun hasSelection(): Boolean = selectedMaterials.isNotEmpty()

    fun cancel() {
        selectedMaterials = emptyList()
        selectedContainerId = -1
        confirmationContainerId = -1
    }

    fun confirm(containerId: Int = selectedContainerId): Boolean {
        if (!hasSelection()) return false
        confirmationContainerId = containerId
        return true
    }

    fun confirmDialog(): List<Material> {
        if (!hasSelection()) return emptyList()
        return takeSelection()
    }

    fun acknowledgeContainerUpdate(containerId: Int): List<Material> {
        if (confirmationContainerId != containerId) return emptyList()
        return takeSelection()
    }

    private fun takeSelection(): List<Material> {
        val confirmed = selectedMaterials
        cancel()
        return confirmed
    }

    data class Material(val key: String, val displayName: String, val owned: Long, val required: Long) {
        fun isAffordable(): Boolean = owned >= required && required > 0
    }
}
