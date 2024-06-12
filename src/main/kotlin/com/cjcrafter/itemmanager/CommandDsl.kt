package com.cjcrafter.itemmanager

import me.deecaad.core.commands.arguments.CommandArgumentType
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Predicate

/**
 * This [scope marker](https://kotlinlang.org/docs/type-safe-builders.html#scope-control-dslmarker)
 * prevents people from using "improper nested types". For example, you cannot
 * put an argument in an argument, or a subcommand in an argument.
 */
@DslMarker
annotation class CommandDsl

/**
 * The KotlinCommand wraps a [CommandBuilder] and lets developers build a
 * command using Kotlin's lambda functions. Not a Kotlin developer? Just
 * use [CommandBuilder] instead.
 */
@CommandDsl
class KotlinCommand(val label: String) {

    var permission: Permission? = null
    var requirements: Predicate<CommandSender>? = null
    var aliases: List<String> = emptyList()
    var arguments: MutableList<me.deecaad.core.commands.Argument<*>> = ArrayList()
    var subcommands: MutableList<me.deecaad.core.commands.CommandBuilder> = ArrayList()
    var executor: me.deecaad.core.commands.CommandExecutor<out CommandSender>? = null
    var description: String = "No description provided"

    fun aliases(vararg aliases: String) {
        this.aliases = aliases.toList()
    }

    fun permission(permission: String) {
        this.permission = Permission(permission)
    }

    fun description(description: String) {
        this.description = description
    }

    fun <T> argument(label: String, type: CommandArgumentType<T>, init: KotlinArgument<T>.() -> Unit) {
        val argument = KotlinArgument(label, type)
        argument.init()

        // After calling argument.init(), the code block inside the {} will
        // be executed. So now we can copy the values into the command argument.
        val temp = if (argument.isRequired) me.deecaad.core.commands.Argument(
            label,
            type
        ) else me.deecaad.core.commands.Argument(label, type, argument.default)
        temp.withPermission(argument.permission)
        temp.withDesc(argument.description)
        if (argument.isReplaceSuggestions)
            temp.replace(argument.suggestions)
        else
            temp.append(argument.suggestions)

        temp.withRequirements(argument.requirements)
        arguments.add(temp as me.deecaad.core.commands.Argument<*>)
    }

    fun subcommand(label: String, init: KotlinCommand.() -> Unit) {
        subcommands.add(command(label, init))
    }

    fun executePlayer(init: (Player, Array<Any?>) -> Unit) {
        executor = me.deecaad.core.commands.CommandExecutor.player(init)
    }

    fun executeEntity(init: (Entity, Array<Any?>) -> Unit) {
        executor = me.deecaad.core.commands.CommandExecutor.entity(init)
    }

    fun executeAny(init: (CommandSender, Array<Any?>) -> Unit) {
        executor = me.deecaad.core.commands.CommandExecutor.any(init)
    }

    fun <T : CommandSender> execute(type: Class<T>, init: BiConsumer<T, Array<Any?>>) {
        executor = object: me.deecaad.core.commands.CommandExecutor<T>(type) {
            override fun execute(sender: T, args: Array<Any?>) {
                init.accept(sender, args)
            }
        }
    }
}

/**
 * The KotlinArgument wraps an [Argument] and lets developers build an
 * argument using kotlin's lambda functions.
 */
@CommandDsl
class KotlinArgument<T>(val label: String, type: CommandArgumentType<T>) {

    var isRequired: Boolean = true
    private var defaultInternal: T? = null
    var permission: Permission? = null
    var description: String = "No description provided"
    internal var isReplaceSuggestions: Boolean = false
    internal var suggestions: Function<me.deecaad.core.commands.CommandData, Array<me.deecaad.core.commands.Tooltip>>? = null
    internal var requirements: Predicate<CommandSender>? = null

    var default: T?
        set(value) {
            isRequired = false
            defaultInternal = value
        }
        get() = defaultInternal

    fun append(suggestions: Function<me.deecaad.core.commands.CommandData, Array<me.deecaad.core.commands.Tooltip>>) {
        this.suggestions = suggestions
    }

    fun replace(suggestions: Function<me.deecaad.core.commands.CommandData, Array<me.deecaad.core.commands.Tooltip>>) {
        this.suggestions = suggestions
        this.isReplaceSuggestions = true
    }

    fun requirements(requirements: Predicate<CommandSender>) {
        this.requirements = requirements
    }
}

fun command(label: String, init: KotlinCommand.() -> Unit): me.deecaad.core.commands.CommandBuilder {
    val builder = KotlinCommand(label)
    builder.init()

    // After calling argument.init(), the code block inside the {} will
    // be executed. So now we can copy the values into the command argument.
    val temp = me.deecaad.core.commands.CommandBuilder(label)
    temp.withPermission(builder.permission)
    temp.withRequirements(builder.requirements)
    temp.withAliases(*builder.aliases.toTypedArray())
    temp.withArguments(builder.arguments)
    temp.withDescription(builder.description)
    temp.executes(builder.executor)

    builder.subcommands.forEach { temp.withSubcommand(it) }

    return temp
}