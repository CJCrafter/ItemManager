package com.cjcrafter.itemmanager

import me.deecaad.core.commands.HelpCommandBuilder
import me.deecaad.core.commands.SuggestionsBuilder
import me.deecaad.core.commands.arguments.EntityListArgumentType
import me.deecaad.core.commands.arguments.IntegerArgumentType
import me.deecaad.core.commands.arguments.StringArgumentType
import me.deecaad.core.file.serializers.ItemSerializer
import me.deecaad.core.utils.StringUtil
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

object Command {

    const val SYM = '\u27A2'

    fun register() {
        val cmd = command("im") {
            aliases("itemmanager")
            permission("itemmanager.admin")
            description("ItemManager main command")

            subcommand("give") {
                permission("itemmanager.give")
                description("Give an item to a player")
                argument("target", EntityListArgumentType()) {
                    description = "The player to give the item to"
                }
                argument("item", StringArgumentType()) {
                    description = "The item to give"
                    append(SuggestionsBuilder.from(ItemSerializer.ITEM_REGISTRY.keys.toTypedArray()))
                }
                argument("amount", IntegerArgumentType()) {
                    description = "The amount of the item to give"
                    default = 1
                }
                executeAny { sender, args ->
                    give(sender, args[0] as List<Entity>, args[1] as String, args[2] as Int)
                }
            }

            subcommand("get") {
                permission("itemmanager.get")
                description("Get an item")
                argument("item", StringArgumentType()) {
                    description = "The item to get"
                    append(SuggestionsBuilder.from(ItemSerializer.ITEM_REGISTRY.keys.toTypedArray()))
                }
                argument("amount", IntegerArgumentType()) {
                    description = "The amount of the item to get"
                    default = 1
                }
                executePlayer { player, args ->
                    give(player, listOf(player), args[0] as String, args[1] as Int)
                }
            }

            subcommand("reload") {
                permission("itemmanager.reload")
                description("Reload the configuration")
                executeAny { sender, _ ->
                    ItemManager.INSTANCE.reload()
                    sender.sendMessage("${ChatColor.GREEN}Reloaded configuration")
                }
            }
        }

        cmd.registerHelp(HelpCommandBuilder.HelpColor.from(ChatColor.GOLD, ChatColor.GRAY, SYM))
        cmd.register()
    }

    fun give(sender: CommandSender, receivers: List<Entity>, itemStr: String, amount: Int) {
        val supplier = ItemSerializer.ITEM_REGISTRY[itemStr]
        if (supplier == null) {
            val didYouMean = StringUtil.didYouMean(itemStr, ItemSerializer.ITEM_REGISTRY.keys)
            sender.sendMessage("${ChatColor.RED}Unknown item '$itemStr'. Did you mean '$didYouMean'?")
            return
        }

        val item = supplier.get()
        item.amount = amount
        var count = 0
        for (entity in receivers) {
            if (entity !is Player)
                continue

            entity.inventory.addItem(item.clone())
            count++
        }

        sender.sendMessage("${ChatColor.GREEN}Gave $count players $amount $itemStr")
    }
}