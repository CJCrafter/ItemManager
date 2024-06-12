package com.cjcrafter.itemmanager

import com.jeff_media.updatechecker.UpdateCheckSource
import com.jeff_media.updatechecker.UpdateChecker
import com.jeff_media.updatechecker.UserAgentBuilder
import me.deecaad.core.MechanicsCore
import me.deecaad.core.file.BukkitConfig
import me.deecaad.core.file.SerializeData
import me.deecaad.core.file.SerializerException
import me.deecaad.core.file.serializers.ItemSerializer
import me.deecaad.core.utils.Debugger
import me.deecaad.core.utils.FileUtil
import me.deecaad.core.utils.LogLevel
import me.deecaad.core.utils.MinecraftVersions
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Supplier

class ItemManager : JavaPlugin() {

    lateinit var debug: Debugger
        private set
    var customItems: MutableSet<String> = mutableSetOf()

    @Override
    override fun onLoad() {
        INSTANCE = this
        setupDebugger()
    }

    @Override
    override fun onEnable() {
        if (!MinecraftVersions.UPDATE_AQUATIC.isAtLeast()) {
            debug.error("This plugin requires at least Minecraft 1.13")
            server.pluginManager.disablePlugin(this)
            return
        }

        writeFiles()
        loadConfig()

        /*
        UpdateChecker(this, UpdateCheckSource.SPIGOT, "TODO")
            .setNotifyOpsOnJoin(true)
            .setUserAgent(UserAgentBuilder().addPluginNameAndVersion())
            .checkEveryXHours(24.0)
            .checkNow()
         */

        Command.register()
    }

    fun reload() {
        // first, remove all items from the global registry
        for (key in customItems)
            ItemSerializer.ITEM_REGISTRY.remove(key)
        customItems.clear()

        // then, reload the configuration
        loadConfig()
    }

    private fun setupDebugger() {
        val logger = logger
        val level = config.getInt("Debug_Level", 2)
        val isPrintTraces = config.getBoolean("Print_Traces", false)
        debug = Debugger(logger, level, isPrintTraces)
        MechanicsCore.debug.level = level
        debug.permission = "itemmanager.errorlog"
        debug.msg = "ItemManager had %s error(s) in console."
    }

    private fun writeFiles() {
        if (!dataFolder.exists() || (dataFolder.listFiles()?.size ?: 0) == 0) {
            debug.info("Copying files from jar (This process may take up to 30 seconds during the first load!)")
            FileUtil.copyResourcesTo(classLoader.getResource("ItemManager"), dataFolder.toPath())
        }

        try {
            FileUtil.ensureDefaults(classLoader.getResource("ItemManager/config.yml"), File(dataFolder, "config.yml"))
        } catch (e: YAMLException) {
            debug.error("ItemManager jar corruption, likely caused by /reload")
        }
    }

    private fun loadConfig() {
        val itemFolder = File(dataFolder, "items")
        if (!itemFolder.exists()) {
            itemFolder.mkdirs()
            return
        }

        // Used in the file visitor to determine if this is the last iteration. During the last
        // iteration, we will log the error if the item could not be loaded.
        var isLast = false

        // A file visitor we can re-use to handle recipes dependent on each other
        val visitor = object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val stream = Files.newInputStream(file)
                val config = YamlConfiguration()

                // Try to load the file into memory
                try {
                    config.load(InputStreamReader(stream, StandardCharsets.UTF_8))
                } catch (e: InvalidConfigurationException) {
                    debug.error("Could not read file '${file.toFile()}'... make sure it is valid YAML", e.message)
                    return FileVisitResult.CONTINUE
                }

                // Iterate over the keys in the file, and load the items
                for (key in config.getKeys(false)) {
                    // Don't try to load the item if it already exists
                    if (ItemSerializer.ITEM_REGISTRY.containsKey(key))
                        continue

                    try {
                        val data = SerializeData(ItemSerializer(), file.toFile(), key, BukkitConfig(config))
                        val item = data.of().serialize(ItemSerializer())!!
                        val supplier = Supplier { item.clone() }
                        ItemSerializer.ITEM_REGISTRY[key] = supplier
                        customItems.add(key)
                    } catch (e: SerializerException) {
                        if (!isLast)
                            continue

                        e.log(debug)
                    }
                }

                return FileVisitResult.CONTINUE
            }
        }

        try {
            val path = FileUtil.PathReference.of(itemFolder.toURI())
            val iterations = 200
            for (i in 0 until iterations) {
                isLast = i == iterations - 1
                Files.walkFileTree(path.path, visitor)
            }
        } catch (e: Throwable) {
            debug.log(LogLevel.ERROR, "Some error occurred whilst reading items folder", e)
        }
    }

    companion object {
        lateinit var INSTANCE: ItemManager
            private set
    }
}