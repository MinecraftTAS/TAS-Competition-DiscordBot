package com.minecrafttas.tascomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;

import net.dv8tion.jda.api.entities.Guild;

public class GuildConfigs {

	private Logger LOGGER;

	private final Properties defaultGuildConfig = createDefaultGuildProperties();
	private final HashMap<Long, Properties> guildConfigs = new HashMap<>();
	private final File configDir = new File("configs/");

	public enum ConfigValues {
		COMPETITION_RUNNING("isCompetitionRunning", "false"),
		SUBMITCHANNEL("submitChannel", null),
		ORGANIZERCHANNEL("organizerChannel", null), 
		PARTICIPATECHANNEL("participateChannel", null);

		private String keyname;
		private String defaultvalue;

		ConfigValues(String channelname, String defaultValue) {
			this.keyname = channelname;
			this.defaultvalue = defaultValue;
		}

		public static Map<String, String> getDefaultValues() {
			Map<String, String> out = new HashMap<>();
			for (ConfigValues configthing : values()) {
				if(configthing.defaultvalue!=null) {
					out.put(configthing.keyname, configthing.defaultvalue);
				}
			}
			return out;
		}

		public static boolean contains(String nameIn) {
			for (ConfigValues configthing : values()) {
				if (configthing.toString().equalsIgnoreCase(nameIn)) {
					return true;
				}
			}
			return false;
		}
		
		public static ConfigValues fromString(String nameIn) {
			for (ConfigValues configthing : values()) {
				if (configthing.toString().equalsIgnoreCase(nameIn)) {
					return configthing;
				}
			}
			return null;
		}
		
	}

	public GuildConfigs(Logger logger) {
		this.LOGGER = logger;
		
		if(!configDir.exists()) {
			configDir.mkdir();
		}
	}

	private Properties createDefaultGuildProperties() {
		Properties prop = new Properties();
		prop.put("isCompetitionRunning", "false");
		prop.putAll(ConfigValues.getDefaultValues());
		return prop;
	}

	public void prepareConfig(Guild guild) {
		Properties guildConfig = new Properties();
		guildConfig.putAll(defaultGuildConfig);

		File configFile = new File(configDir, guild.getId() + ".xml");
		if (configFile.exists()) {
			guildConfig = loadConfig(guild, configFile);
		} else {
			LOGGER.info("Creating default config...");
			saveConfig(guild, guildConfig);
		}
		guildConfigs.put(guild.getIdLong(), guildConfig);
	}

	public void saveConfig(Guild guild) {
		saveConfig(guild, guildConfigs.get(guild.getIdLong()));
	}
	
	public void saveConfig(Guild guild, Properties config) {
		
		LOGGER.info("Saving config for guild {}...", guild.getName());
		File configFile = new File(configDir, guild.getId() + ".xml");

		try {
			FileOutputStream fos = new FileOutputStream(configFile);
			config.storeToXML(fos, "Properties for guild: " + guild.getName(), "UTF-8");
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Properties loadConfig(Guild guild) {
		return loadConfig(guild, new File(configDir, guild.getId() + ".xml"));
	}
	
	public Properties loadConfig(Guild guild, File configFile) {
		LOGGER.info("Loading config for guild {}...", guild.getName());
		Properties guildConfig = new Properties();
		try {
			FileInputStream fis = new FileInputStream(configFile);
			guildConfig.loadFromXML(fis);
			fis.close();
		} catch (InvalidPropertiesFormatException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return guildConfig;
	}
	
	public void setValue(Guild guild, String key, String value) throws Exception{
		if(ConfigValues.contains(key)) {
			setValue(guild, ConfigValues.fromString(key), value);
		} else {
			throw new Exception(key+" doesn't exist");
		}
	}
	
	public void setValue(Guild guild, ConfigValues key, String value) throws NullPointerException {
		Properties property = guildConfigs.get(guild.getIdLong());
		if(property==null) {
			throw new NullPointerException("Can't set the property, because it can't find a guild config");
		}
		property.put(key.keyname, value);
		saveConfig(guild);
	}
	
	public void removeValue(Guild guild, String key) throws Exception {
		if(ConfigValues.contains(key)) {
			removeValue(guild, ConfigValues.fromString(key));
		} else {
			throw new Exception(key+" doesn't exist");
		}
	}
	
	public void removeValue(Guild guild, ConfigValues key) throws NullPointerException {
		Properties property = guildConfigs.get(guild.getIdLong());
		if(property==null) {
			throw new NullPointerException("Can't remove the property, because it can't find a guild config");
		}
		if(key.defaultvalue==null) {
			property.remove(key.keyname);
		}else {
			property.put(key.keyname, key.defaultvalue);
		}
		saveConfig(guild);
	}
	
	public boolean hasValue(Guild guild, ConfigValues key) {
		return guildConfigs.get(guild.getIdLong()).containsKey(key.keyname);
	}
	
	public String getValue(Guild guild, ConfigValues key) {
		return guildConfigs.get(guild.getIdLong()).getProperty(key.keyname);
	}
	
	public File getConfigdir() {
		return configDir;
	}

}
