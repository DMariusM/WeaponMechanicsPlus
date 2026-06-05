/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.modifiers.attachments

import com.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlus
import me.deecaad.core.file.*
import me.deecaad.core.file.serializers.ItemSerializer
import me.deecaad.core.mechanics.Mechanics
import me.deecaad.weaponmechanics.utils.CustomTag
import com.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlusAPI
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.ModifierBase
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.Whitelist
import me.deecaad.core.file.simple.EnumValueSerializer
import me.deecaad.core.file.simple.StringSerializer
import me.deecaad.core.mechanics.MechanicManager
import org.bukkit.inventory.ItemStack
import kotlin.jvm.optionals.getOrNull

class Attachment : ModifierBase {

    lateinit var attachmentTitle: String
    var maximumStackAmount = 1
    lateinit var item: ItemStack
    var attachmentRequireList: Set<String> = setOf()
    var attachmentDenyList: Set<String> = setOf()
    var weaponWhitelist: Whitelist<String>? = null
    var armorWhitelist: Whitelist<String>? = null
    var denyMechanics: MechanicManager? = null
    var equipMechanics: MechanicManager? = null
    var dequipMechanics: MechanicManager? = null
    var unlockable: Unlockable? = null

    /**
     * Default constructor for serializer
     */
    constructor()

    constructor(
        attachmentTitle: String,
        maximumStackAmount: Int,
        item: ItemStack,
        attachmentRequireList: Set<String>,
        attachmentDenyList: Set<String>,
        weaponWhitelist: Whitelist<String>?,
        armorWhitelist: Whitelist<String>?,
        unlockable: Unlockable?,
        denyMechanics: MechanicManager?,
        equipMechanics: MechanicManager?,
        dequipMechanics: MechanicManager?,
    ) {
        this.attachmentTitle = attachmentTitle
        this.maximumStackAmount = maximumStackAmount
        this.item = item
        this.attachmentRequireList = attachmentRequireList
        this.attachmentDenyList = attachmentDenyList
        this.weaponWhitelist = weaponWhitelist
        this.armorWhitelist = armorWhitelist
        this.unlockable = unlockable
        this.denyMechanics = denyMechanics
        this.equipMechanics = equipMechanics
        this.dequipMechanics = dequipMechanics
    }

    /**
     * Returns a copy of the attachment item.
     */
    fun generateItem() = item.clone()

    fun canAttach(item: ItemStack, itemTitle: String, isWeapon: Boolean): Boolean {
        val whitelist = if (isWeapon) weaponWhitelist else armorWhitelist
        if (whitelist == null || !whitelist.isWhitelisted(itemTitle)) return false

        val attached = WeaponMechanicsPlusAPI.getAttachments(item).orEmpty()
        val attachedTitles = HashSet<String>(attached.size)
        var duplicateCount = 0

        for (attachment in attached) {
            attachedTitles.add(attachment.attachmentTitle)

            if (attachment === this) {
                duplicateCount++
                continue
            }

            // Some attachments are not compatible with each other
            if (attachmentDenyList.contains(attachment.attachmentTitle)) return false
        }

        // Every required attachment must already be attached. Unrelated
        // attachments do not affect this requirement check
        if (!attachedTitles.containsAll(attachmentRequireList)) return false

        // Cannot attach the same attachment multiple times
        return duplicateCount < maximumStackAmount
    }

    fun attach(weapon: ItemStack?) {
        val array = CustomTag.ATTACHMENTS.getStringArray(weapon) ?: emptyArray()

        val attachmentConfig = WeaponMechanicsPlus.getInstance().attachmentConfiguration
        val attachments: MutableList<Attachment> = ArrayList(array.size + 1)
        for (id in array) {
            val attachment = attachmentConfig.get<Attachment>(id)

            // attachment is null when the admin deletes an attachment from
            // config after it is attached to a weapon.
            if (attachment != null)
                attachments.add(attachment)
        }

        // Add the new attachment and sort
        attachments.add(this)
        attachments.sort()
        CustomTag.ATTACHMENTS.setStringArray(weapon, attachments.map { it.attachmentTitle }.toTypedArray())
    }

    @Throws(SerializerException::class)
    override fun serialize(data: SerializeData): Attachment {
        val attachmentTitle = data.key!!
        val maximumStackAmount = data.of("Maximum_Stack_Amount").assertRange(1, 100).getInt().orElse(1)

        // Make sure to use CustomTag during serialization so recipe works
        data.of("Item").assertExists()
        val tags = mapOf(Pair(CustomTag.ATTACHMENT_TITLE.key, attachmentTitle))
        val item = ItemSerializer().serializeWithTags(data.move("Item"), tags)

        var weaponWhitelist: Whitelist<String>? = null
        var armorWhitelist: Whitelist<String>? = null
        if (data.has("Denying")) {
            if (!data.has("Denying.Weapon_Whitelist") && !data.has("Denying.Armor_Whitelist")) {
                throw data.exception("Denying", "Must have either Weapon_Whitelist or Armor_Whitelist")
            }

            if (data.has("Denying.Weapon_Whitelist")) {
                val isWeaponWhitelist = data.of("Denying.Weapon_Whitelist").getBool().orElse(false)
                val weapons = data.ofList("Denying.Weapons")
                    .addArgument(StringSerializer())
                    .requireAllPreviousArgs()
                    .assertList()
                    .map { it[0].get().toString() }
                weaponWhitelist = Whitelist(isWeaponWhitelist, weapons)
            }

            if (data.has("Denying.Armor_Whitelist")) {
                val isArmorWhitelist = data.of("Denying.Armor_Whitelist").getBool().orElse(false)
                val armors = data.ofList("Denying.Armors")
                    .addArgument(StringSerializer())
                    .requireAllPreviousArgs()
                    .assertList()
                    .map { it[0].get().toString() }
                armorWhitelist = Whitelist(isArmorWhitelist, armors)
            }
        }

        val attachmentRequireList: MutableSet<String> = HashSet()
        val attachmentDenyList: MutableSet<String> = HashSet()

        val tempSplitData = data.ofList("Denying.Attachments")
            .addArgument(EnumValueSerializer(State::class.java, false))
            .addArgument(StringSerializer())
            .requireAllPreviousArgs()
            .assertList()
        for (split in tempSplitData) {
            // EnumValueSerializer always returns a List (to support wildcards),
            // so we grab the single state out of it.
            val state = (split[0].get() as List<*>).firstOrNull() as? State
                ?: throw data.exception("Denying.Attachments", "Expected DENY or REQUIRE")
            val otherAttachment = split[1].get().toString()
            when (state) {
                State.DENY -> attachmentDenyList.add(otherAttachment)
                State.REQUIRE -> attachmentRequireList.add(otherAttachment)
            }
        }

        val unlockable = data.of("Unlockable").serialize(Unlockable::class.java).getOrNull()
        val denyMechanics = data.of("Denying.Mechanics").serialize(MechanicManager::class.java).getOrNull()
        val equipMechanics = data.of("Attach_Mechanics").serialize(MechanicManager::class.java).getOrNull()
        val dequipMechanics = data.of("Detach_Mechanics").serialize(MechanicManager::class.java).getOrNull()

        val returnValue = Attachment(attachmentTitle, maximumStackAmount, item, attachmentRequireList, attachmentDenyList, weaponWhitelist, armorWhitelist, unlockable, denyMechanics, equipMechanics, dequipMechanics)

        val base = super.serialize(data)
        returnValue.priority = base.priority
        returnValue.weaponModifier = base.weaponModifier
        returnValue.perWeaponModifiers = base.perWeaponModifiers
        returnValue.armorModifier = base.armorModifier
        return returnValue
    }

    private enum class State {
        DENY, REQUIRE
    }
}