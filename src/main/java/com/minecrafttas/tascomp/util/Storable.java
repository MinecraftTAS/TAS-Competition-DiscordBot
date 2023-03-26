package com.minecrafttas.tascomp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import org.slf4j.Logger;

import net.dv8tion.jda.api.entities.Guild;

public class Storable {
	private String name;
	
	protected final HashMap<Long, Properties> guildProperties = new HashMap<>();
	protected final File storageDir;
	private final Logger LOGGER;
	
	public Storable(String name, File directory, Logger logger) {
		this.name = name;
		this.storageDir = directory;
		if(!storageDir.exists()) {
			storageDir.mkdir();
		}
		this.LOGGER = logger;
	}
	
	protected void setName(String name) {
		this.name = name;
	}
	
	protected String getName() {
		return name;
	}
	
	public void loadForGuild(Guild guild) {
		Properties prop = new Properties();

		File submissionFile = new File(storageDir, guild.getId() + ".xml");
		if (submissionFile.exists()) {
			prop = load(guild, submissionFile);
			guildProperties.put(guild.getIdLong(), prop);
		}
	}
	
	public void save(Guild guild, Properties prop) {

		LOGGER.info("{{}} Saving {}", guild.getName(), name);
		File submissionFile = new File(storageDir, guild.getId() + ".xml");

		try {
			FileOutputStream fos = new FileOutputStream(submissionFile);
			prop.storeToXML(fos, String.format("Guild {} for guild: {}", name, guild.getName()), "UTF-8");
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected Properties load(Guild guild, File submissionFile) {
		LOGGER.info("{{}} Loading {}", guild.getName(), name);
		Properties guildConfig = new Properties();
		try {
			FileInputStream fis = new FileInputStream(submissionFile);
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
	
	protected void remove(Guild guild) {
		File submissionFile = new File(storageDir, guild.getId() + ".xml");
		if (submissionFile.exists()) {
			submissionFile.delete();
		}
	}
}
