package com.minecrafttas.tascomp.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;

import javax.security.auth.login.LoginException;

import com.minecrafttas.tascomp.TASCompBot;

public class Main {

	private static final File propertiesFile = new File("bot3.properties");
	
	public static void main(String[] args) throws LoginException, InterruptedException, FileNotFoundException, IOException {
		/* Load Configuration from File */
		final Properties configuration = new Properties();
		if (!propertiesFile.exists()) loadDefaultConfiguration();
		configuration.load(new FileInputStream(propertiesFile));
		
		/* Create and run Bot */
		final TASCompBot bot = new TASCompBot(configuration);
		new Thread(bot).run();
	}

	public static void loadDefaultConfiguration() throws IOException {
		propertiesFile.createNewFile();
		FileOutputStream stream = new FileOutputStream(propertiesFile);
		stream.write("\ntoken=".getBytes(Charset.defaultCharset()));
		stream.close();
		System.exit(0);
	}
	
}
