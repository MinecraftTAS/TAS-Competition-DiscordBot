package com.minecrafttas.tascomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.minecrafttas.tascomp.util.EmoteWrapper;
import com.minecrafttas.tascomp.util.Util;
import com.vdurmont.emoji.EmojiManager;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;

public class TASCompBot extends ListenerAdapter implements Runnable {

	private static TASCompBot instance;
	private final JDA jda;
	private final Properties configuration;
	private final Properties defaultCuildConfig = createDefaultGuildProperties();
	private static final HashMap<Long, Properties> guildConfigs = new HashMap<>();
	private static final File configDir = new File("configs/");
	private static final Logger LOGGER = LoggerFactory.getLogger("TAS Competition");
	
	public TASCompBot(Properties configuration) throws InterruptedException, LoginException {
		instance=this;
		this.configuration = configuration;
		final JDABuilder builder = JDABuilder.createDefault(this.configuration.getProperty("token"))
				.setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this);
		this.jda = builder.build();
		this.jda.awaitReady();
	}

	@Override
	public void run() {
		/* Register the Commands */
		LOGGER.info("Preparing bot...");
		if(!configDir.exists()) {
			configDir.mkdir();
		}
		
		for (Guild guild : jda.getGuilds()) {
			prepareGuild(guild);
		}
		
		LOGGER.info("Done preparing bot!");
	}
	
	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		LOGGER.info("Bot joined new guild {}.", event.getGuild().getName());
		prepareGuild(event.getGuild());
	}
	
	private void prepareGuild(Guild guild) {
		LOGGER.info("Preparing guild {}...", guild.getName());
		prepareCommands(guild);
		Properties guildConfig = new Properties();
		guildConfig.putAll(defaultCuildConfig);
		
		File configFile = new File(configDir, guild.getId()+".xml"); 
		if(configFile.exists()) {
			guildConfig = loadConfig(guild, configFile);
		} else {
			LOGGER.info("Creating default config...");
			saveConfig(guild, guildConfig);
		}
		guildConfigs.put(guild.getIdLong(), guildConfig);
		LOGGER.info("Done preparing guild {}!", guild.getName());
	}

	private void prepareCommands(Guild guild) {
		LOGGER.info("Preparing commands...");
		CommandListUpdateAction updater = guild.updateCommands();
		
		// =========================Set Channel
		CommandDataImpl setChannelCommand = new CommandDataImpl("setchannel", "Set's the current channel to the bots channel");
		setChannelCommand.setDefaultEnabled(false);
		
		OptionData typeOption = new OptionData(OptionType.STRING, "type", "The type of information channel. Can be ORGANIZER, SUBMIT", true);
		
		SubcommandData addSubCommand = new SubcommandData("add", "Adds this channel to the config");
		addSubCommand.addOptions(typeOption);
		
		SubcommandData removeSubCommand = new SubcommandData("remove", "Removes this channel from the config");
		removeSubCommand.addOptions(typeOption);
		
		SubcommandData listSubCommand = new SubcommandData("list", "Lists the current modifiers for this channel");
		
		setChannelCommand.addSubcommands(addSubCommand, removeSubCommand, listSubCommand);
		
		// =========================
		
		updater.addCommands(setChannelCommand);
		updater.queue();
		LOGGER.info("Done preparing commands!");
	}
	
	public static Properties loadConfig(Guild guild, File configFile) {
		LOGGER.info("Loading config for guild {}...", guild.getName());
		Properties guildConfig=new Properties();
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
	
	public static void saveConfig(Guild guild, Properties config) {
		LOGGER.info("Saving config for guild {}...", guild.getName());
		File configFile = new File(configDir, guild.getId()+".xml"); 
		try {
			FileOutputStream fos=new FileOutputStream(configFile);
			config.storeToXML(fos, "Properties for guild: " + guild.getName(), "UTF-8");
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Properties createDefaultGuildProperties() {
		Properties prop = new Properties();
		prop.put("isCompetitionRunning", "false");
		prop.put("submitChannel", "0");
		prop.put("organiserChannel", "0");
		return prop;
	}
	
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		LOGGER.info("Running slash command {}", event.getCommandPath());
		event.deferReply().queue(hook -> {
			Util.sendDeletableMessage(event.getChannel(), "Nothing useful yet");
			hook.deleteOriginal().queue();
		});
	}
	
	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event) {
		if (!Util.isThisUserThisBot(event.getUser())) {

			ReactionEmote reactionEmote = event.getReactionEmote();

			if (EmoteWrapper.getReactionEmoteId(reactionEmote).equals(EmojiManager.getForAlias(":x:").getUnicode())) {

				event.retrieveMessage().queue(msg -> {
					if (Util.isThisUserThisBot(msg.getAuthor())) {

						if (Util.hasBotReactedWith(msg, EmojiManager.getForAlias(":x:").getUnicode())) {
							Util.deleteMessage(msg);
						}
					}
				});
			}
		}
	}
	
	public static TASCompBot getBot() {
		return instance;
	}

	public JDA getJDA() {
		return jda;
	}
}
