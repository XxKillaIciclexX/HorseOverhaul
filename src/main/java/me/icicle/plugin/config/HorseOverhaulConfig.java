package me.icicle.plugin.config;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;

public final class HorseOverhaulConfig {

    private static final String FILE_NAME = "HorseOverhaul.properties";
    private static final String SADDLE_STORAGE_SLOTS_KEY = "saddle_storage_slots";
    private static final String SADDLED_HORSE_PETTING_ENABLED_KEY = "saddled_horse_petting_enabled";
    private static final short DEFAULT_SADDLE_STORAGE_SLOTS = 9;
    private static final boolean DEFAULT_SADDLED_HORSE_PETTING_ENABLED = false;
    private static final short HORSE_TOP_ROW_SLOTS = 9;

    private final short saddleStorageSlots;
    private final short saddleStorageRows;
    private final short horseInventorySlots;
    private final short horseInventoryRows;
    private final boolean saddledHorsePettingEnabled;

    private HorseOverhaulConfig(short saddleStorageSlots, boolean saddledHorsePettingEnabled) {
        this.saddleStorageSlots = saddleStorageSlots;
        this.saddleStorageRows = (short) (saddleStorageSlots / HORSE_TOP_ROW_SLOTS);
        this.horseInventorySlots = (short) (HORSE_TOP_ROW_SLOTS + saddleStorageSlots);
        this.horseInventoryRows = (short) (1 + saddleStorageRows);
        this.saddledHorsePettingEnabled = saddledHorsePettingEnabled;
    }

    public static HorseOverhaulConfig load(Path pluginFile, HytaleLogger logger) {
        Path configPath = resolveConfigPath(pluginFile);
        ensureConfigExists(configPath, logger);

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            log(logger, Level.WARNING, "Failed to read %s, using default saddle storage size %s.", configPath, DEFAULT_SADDLE_STORAGE_SLOTS);
            return defaultConfig();
        }

        return new HorseOverhaulConfig(
                parseSaddleStorageSlots(properties.getProperty(SADDLE_STORAGE_SLOTS_KEY), configPath, logger),
                parseBoolean(
                        properties.getProperty(SADDLED_HORSE_PETTING_ENABLED_KEY),
                        configPath,
                        SADDLED_HORSE_PETTING_ENABLED_KEY,
                        DEFAULT_SADDLED_HORSE_PETTING_ENABLED,
                        logger
                )
        );
    }

    public static HorseOverhaulConfig defaultConfig() {
        return new HorseOverhaulConfig(
                DEFAULT_SADDLE_STORAGE_SLOTS,
                DEFAULT_SADDLED_HORSE_PETTING_ENABLED
        );
    }

    public short getSaddleStorageSlots() {
        return saddleStorageSlots;
    }

    public short getSaddleStorageRows() {
        return saddleStorageRows;
    }

    public short getHorseInventorySlots() {
        return horseInventorySlots;
    }

    public short getHorseInventoryRows() {
        return horseInventoryRows;
    }

    public boolean isSaddledHorsePettingEnabled() {
        return saddledHorsePettingEnabled;
    }

    private static Path resolveConfigPath(Path pluginFile) {
        Path absolutePluginFile = pluginFile == null ? Path.of(FILE_NAME).toAbsolutePath() : pluginFile.toAbsolutePath();
        Path parent = absolutePluginFile.getParent();
        return (parent == null ? Path.of(FILE_NAME) : parent.resolve(FILE_NAME)).normalize();
    }

    private static void ensureConfigExists(Path configPath, HytaleLogger logger) {
        if (Files.exists(configPath)) {
            return;
        }

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (InputStream inputStream = HorseOverhaulConfig.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
                if (inputStream != null) {
                    Files.copy(inputStream, configPath);
                } else {
                    Files.writeString(configPath, buildDefaultConfigContents(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException exception) {
            log(logger, Level.WARNING, "Failed to create default config at %s, falling back to in-memory defaults.", configPath);
        }
    }

    private static short parseSaddleStorageSlots(String rawValue, Path configPath, HytaleLogger logger) {
        if (rawValue == null || rawValue.isBlank()) {
            log(logger, Level.WARNING, "%s is missing %s, using default saddle storage size %s.", configPath, SADDLE_STORAGE_SLOTS_KEY, DEFAULT_SADDLE_STORAGE_SLOTS);
            return DEFAULT_SADDLE_STORAGE_SLOTS;
        }

        final int parsedValue;
        try {
            parsedValue = Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            log(logger, Level.WARNING, "%s has invalid %s value '%s'; it must be a positive multiple of 9. Using default %s.", configPath, SADDLE_STORAGE_SLOTS_KEY, rawValue, DEFAULT_SADDLE_STORAGE_SLOTS);
            return DEFAULT_SADDLE_STORAGE_SLOTS;
        }

        if (parsedValue <= 0 || parsedValue % 9 != 0 || parsedValue > Short.MAX_VALUE - HORSE_TOP_ROW_SLOTS) {
            log(logger, Level.WARNING, "%s has unsupported %s value '%s'; it must be a positive multiple of 9. Using default %s.", configPath, SADDLE_STORAGE_SLOTS_KEY, parsedValue, DEFAULT_SADDLE_STORAGE_SLOTS);
            return DEFAULT_SADDLE_STORAGE_SLOTS;
        }

        return (short) parsedValue;
    }

    private static boolean parseBoolean(
            String rawValue,
            Path configPath,
            String key,
            boolean defaultValue,
            HytaleLogger logger
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            log(logger, Level.WARNING, "%s is missing %s, using default %s.", configPath, key, defaultValue);
            return defaultValue;
        }

        String normalized = rawValue.trim().toLowerCase();
        if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
            return false;
        }

        log(logger, Level.WARNING, "%s has invalid %s value '%s'; using default %s.", configPath, key, rawValue, defaultValue);
        return defaultValue;
    }

    private static void log(HytaleLogger logger, Level level, String message, Object... args) {
        if (logger != null) {
            logger.at(level).log(message, args);
        }
    }

    private static String buildDefaultConfigContents() {
        return "# Horse Overhaul configuration" + System.lineSeparator()
                + "#" + System.lineSeparator()
                + "# saddle_storage_slots controls how many storage slots the saddle bag has." + System.lineSeparator()
                + "# It must be a positive multiple of 9." + System.lineSeparator()
                + "# Rows are calculated automatically as saddle_storage_slots / 9." + System.lineSeparator()
                + SADDLE_STORAGE_SLOTS_KEY + "=" + DEFAULT_SADDLE_STORAGE_SLOTS + System.lineSeparator()
                + System.lineSeparator()
                + "# saddled_horse_petting_enabled controls whether saddled horses can still be petted." + System.lineSeparator()
                + "# false keeps mount/inventory interactions from being interrupted by petting." + System.lineSeparator()
                + "# Restart the server after changing this setting." + System.lineSeparator()
                + SADDLED_HORSE_PETTING_ENABLED_KEY + "=" + DEFAULT_SADDLED_HORSE_PETTING_ENABLED + System.lineSeparator();
    }
}
