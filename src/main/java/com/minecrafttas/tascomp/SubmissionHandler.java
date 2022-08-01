package com.minecrafttas.tascomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import org.slf4j.Logger;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.Util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

public class SubmissionHandler {

	private Logger LOGGER;
	private final HashMap<Long, Properties> guildSubmissions = new HashMap<>();
	private final File submissionDir = new File("submissions/");

	public SubmissionHandler(Logger logger) {
		this.LOGGER = logger;
		if (!submissionDir.exists()) {
			submissionDir.mkdir();
		}
	}

	public void loadSubmissionsForGuild(Guild guild) {
		Properties prop = new Properties();

		File submissionFile = new File(submissionDir, guild.getId() + ".xml");
		if (submissionFile.exists()) {
			prop = loadSubmissions(guild, submissionFile);
			guildSubmissions.put(guild.getIdLong(), prop);
		}
	}

	private Properties loadSubmissions(Guild guild, File submissionFile) {
		LOGGER.info("Loading submissions for guild {}...", guild.getName());
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

	public void submit(Guild guild, User author, Message submitMessage, String raw) {
		Message submission = Util.constructMessageWithAuthor(submitMessage, "New submission!", raw, TASCompBot.color);
		submitInner(guild, author, submission, raw, true);
	}

	public void submit(Guild guild, User author, String raw) {
		Message submission = Util.constructMessageWithAuthor(author, "New manual submission!", raw, TASCompBot.color);
		submitInner(guild, author, submission, raw, false);
	}

	private void submitInner(Guild guild, User author, Message submitMessage, String raw, boolean dm) {

		long guildID = guild.getIdLong();

		String authorTag = author.getAsTag();

		GuildConfigs config = TASCompBot.getBot().getGuildConfigs();

		String submitChannelID = config.getValue(guild, ConfigValues.SUBMITCHANNEL);
		MessageChannel submitChannel = null;
		if (submitChannelID == null) {
			return;
		} else {
			submitChannel = (MessageChannel) guild.getGuildChannelById(submitChannelID);
		}

		Properties guildSubmission = guildSubmissions.containsKey(guildID) ? guildSubmissions.get(guildID) : new Properties();

		boolean flag = guildSubmission.containsKey(authorTag);

		if (flag) {
			String submission = guildSubmission.getProperty(authorTag);
			String messageID = submission.split(";", 2)[0];
			try {
				submitChannel.retrieveMessageById(messageID).queue(msg -> {
					MessageEmbed messageEmbed = msg.getEmbeds().get(0);
					EmbedBuilder ebuilder = new EmbedBuilder(messageEmbed);

					ebuilder.setTitle("~~Previous submission~~");
					ebuilder.setColor(0xB90000);

					msg.editMessageEmbeds(ebuilder.build()).queue();
				});
			} catch (Exception e) {
				LOGGER.warn("Tried to get message {} but it was not found in the channel {}. Ignoring it...", messageID, submitChannel.getName());
			}
		}

		// Send the message to the submission channel
		submitChannel.sendMessage(submitMessage).queue(msg -> {
			String value = msg.getIdLong() + ";" + raw;

			guildSubmission.put(authorTag, value);
			saveSubmission(guild, guildSubmission);

			guildSubmissions.put(guild.getIdLong(), guildSubmission);

			String replyText = flag ? "Your submission was updated!" : "Your submission was saved!";

			if (dm) {
				Util.sendDeletableDirectMessage(author, replyText);
			}
		});
	}

	private void saveSubmission(Guild guild, Properties submission) {

		LOGGER.info("Saving submissions for guild {}...", guild.getName());
		File submissionFile = new File(submissionDir, guild.getId() + ".xml");

		try {
			FileOutputStream fos = new FileOutputStream(submissionFile);
			submission.storeToXML(fos, "Guild submissions for guild: " + guild.getName(), "UTF-8");
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void clearSubmission(Guild guild, User author, MessageChannel channel) {
		Properties guildSubmission = guildSubmissions.get(guild.getIdLong());
		guildSubmission.remove(author.getAsTag());
		saveSubmission(guild, guildSubmission);
		Util.sendDeletableMessage(channel, "Cleared submission of " + author.getAsTag());
	}

	public void clearAllSubmissions(Guild guild, MessageChannel channel) {
		File submissionFile = new File(submissionDir, guild.getId() + ".xml");
		if (submissionFile.exists()) {
			submissionFile.delete();
		}
		if (guildSubmissions.get(guild.getIdLong()) != null) {
			guildSubmissions.remove(guild.getIdLong());
		} else {
			Util.sendDeletableMessage(channel, "Nothing to clear!");
			return;
		}
		Util.sendDeletableMessage(channel, "Cleared all submissions!");
	}

	public void sendSubmissionList(Guild guild, MessageChannel channel) {
		if (guildSubmissions.get(guild.getIdLong()) == null) {
			Util.sendDeletableMessage(channel, "Submission list is empty!");
			return;
		}

		MessageBuilder builder = new MessageBuilder(getSubmissionList(guild));
		channel.sendMessage(builder.build()).queue();
	}

	private MessageEmbed getSubmissionList(Guild guild) {
		Properties submission = guildSubmissions.get(guild.getIdLong());
		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle("All submissions!");
		builder.setColor(0x00EAFF);

		submission.forEach((left, right) -> {
			String author = (String) left;
			String message = (String) right;
			builder.addField(author, message.split(";", 2)[1], false);
		});
		return builder.build();
	}

}
