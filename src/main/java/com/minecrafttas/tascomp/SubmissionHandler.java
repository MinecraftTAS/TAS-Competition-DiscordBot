package com.minecrafttas.tascomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.Util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

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
		MessageCreateData submission = Util.constructMessageWithAuthor(submitMessage, "New submission!", raw, TASCompBot.color);
		
		for(Attachment attach : submitMessage.getAttachments()) {
			raw+="\n"+attach.getUrl();
		}
		
		submitInner(guild, author, submission, raw, true);
	}

	public void submit(Guild guild, User author, String raw) {
		MessageCreateData submission = Util.constructMessageWithAuthor(author, "New manual submission!", raw, TASCompBot.color);
		submitInner(guild, author, submission, raw, false);
	}

	private void submitInner(Guild guild, User author, MessageCreateData submission2, String raw, boolean dm) {

		long guildID = guild.getIdLong();

		String authorTag = author.getAsTag();

		GuildConfigs config = TASCompBot.getBot().getGuildConfigs();

		String submitChannelID = config.getValue(guild, ConfigValues.SUBMITCHANNEL);
		MessageChannel submitChannel = null;
		if (submitChannelID == null) {
			return;
		}
		submitChannel = (MessageChannel) guild.getGuildChannelById(submitChannelID);

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
		submitChannel.sendMessage(submission2).queue(msg -> {
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

	public void clearSubmission(GenericCommandInteractionEvent event, User author) {
		Properties guildSubmission = guildSubmissions.get(event.getGuild().getIdLong());
		guildSubmission.remove(author.getAsTag());
		saveSubmission(event.getGuild(), guildSubmission);
		Util.sendDeletableReply(event, "Cleared submission of " + author.getAsTag());
	}

	public void clearAllSubmissions(GenericCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		File submissionFile = new File(submissionDir, guild.getId() + ".xml");
		if (submissionFile.exists()) {
			submissionFile.delete();
		}
		if (guildSubmissions.get(guild.getIdLong()) != null) {
			guildSubmissions.remove(guild.getIdLong());
		} else {
			Util.sendDeletableReply(event, "Nothing to clear!");
			return;
		}
		Util.sendDeletableReply(event, "Cleared all submissions!");
	}

	public void sendSubmissionList(GenericCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		if (guildSubmissions.get(guild.getIdLong()) == null) {
			Util.sendDeletableReply(event, "Submission list is empty!");
			return;
		}
		
		List<MessageEmbed> embeds = getSubmissionList(guild);
		for(MessageEmbed embed: embeds) {
			MessageCreateBuilder builder = new MessageCreateBuilder().setEmbeds(embed);
			Util.sendDeletableReply(event, builder.build());
		}
	}

	private List<MessageEmbed> getSubmissionList(Guild guild) {
		Properties submission = guildSubmissions.get(guild.getIdLong());
		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle("All submissions!");
		int color = 0x00EAFF;
		builder.setColor(color);

		List<MessageEmbed> embeds = new ArrayList<>();
		
		Set<Object> keys = submission.keySet();
		
		int count=0;
		
		for(Object key: keys) {
			count++;
			if(count%25==0) {
				embeds.add(builder.build());
				builder = new EmbedBuilder();
				builder.setColor(color);
			}
			Object value = submission.get(key);
			
			String author = (String) key;
			String message = (String) value;
			builder.addField(author, message.split(";", 2)[1], false);
		}
		embeds.add(builder.build());
		return embeds;
	}

	public boolean hasSubmitted(User author, Guild guild) {
		Properties guildSubmission = guildSubmissions.get(guild.getIdLong());
		if(guildSubmission==null) {
			return false;
		}
		return guildSubmission.containsKey((Object)author.getAsTag());
	}
}
