package com.minecrafttas.tascomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;

import com.minecrafttas.tascomp.util.Util;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.Timestamp;

public class ScheduleMessageHandler {

	private Logger LOGGER;

	private HashMap<Long, Properties> scheduledMessages = new HashMap<>();

	private final HashMap<Long, TimerTask> timerTasks = new HashMap<>();

	private File scheduledDir = new File("scheduled/");

	private static Timer timer = new Timer("Schedule Message Timer");

	public ScheduleMessageHandler(Logger logger) {
		LOGGER = logger;
		if (!scheduledDir.exists()) {
			scheduledDir.mkdir();
		}
	}

	public void loadScheduledMessagesForGuild(Guild guild) {
		Properties prop = new Properties();

		File submissionFile = new File(scheduledDir, guild.getId() + ".xml");
		if (submissionFile.exists()) {
			prop = loadScheduledMessages(guild, submissionFile);
			scheduledMessages.put(guild.getIdLong(), prop);
			prop.forEach((target, fileString) -> {
				long targetId = Long.parseLong((String) target);
				scheduleMessage(guild, (String) fileString, targetId);
			});
		}
	}

	private Properties loadScheduledMessages(Guild guild, File submissionFile) {
		LOGGER.info("Loading scheduled messages for guild {}...", guild.getName());
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

	public void saveScheduledMessages(Guild guild, Properties scheduledMessage) {

		LOGGER.info("Saving scheduled messages for guild {}...", guild.getName());
		File scheduleMessageConfig = new File(scheduledDir, guild.getId() + ".xml");

		try {
			FileOutputStream fos = new FileOutputStream(scheduleMessageConfig);
			scheduledMessage.storeToXML(fos, "Scheduled Messages for guild: " + guild.getName(), "UTF-8");
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void scheduleMessage(MessageChannel sourceChannel, String messageId, MessageChannel targetChannel, String timestampString) throws Exception {
		sourceChannel.retrieveMessageById(messageId).queue(msg -> {

			Timestamp timestampTarget = TimeFormat.parse(timestampString);

			Date date = new Date(timestampTarget.getTimestamp());

			if (date.compareTo(Date.from(Instant.now())) < 0) {
				LOGGER.warn("Tried to add scheduled message but the scheduled time is already over: {}", date.toString());
				Util.sendSelfDestructingMessage(sourceChannel, "Can't add a scheduled message. The timestamp lies in the past!", 10);
				return;
			}

			TimerTask task = new TimerTask() {

				@Override
				public void run() {
					try {
						targetChannel.sendMessageEmbeds(MD2Embed.parseEmbed(msg.getContentRaw(), TASCompBot.color).build()).queue();
						;
					} catch (Exception e) {
						e.printStackTrace();
					}

					HashMap<Long, TimerTask> temporaryHashmap = new HashMap<>(timerTasks);
					temporaryHashmap.forEach((msgId, task) -> {
						if (task.equals(this)) {
							timerTasks.remove(msgId);
							scheduledMessages.remove(msgId);
						}
					});
				}
			};
			timer.schedule(task, date);

			MessageBuilder previewMessageBuilder = null;
			try {
				previewMessageBuilder = new MessageBuilder(MD2Embed.parseEmbed(msg.getContentRaw(), TASCompBot.color).build());
			} catch (Exception e) {
				e.printStackTrace();
			}

			previewMessageBuilder.setContent("Scheduled message to " + TimeFormat.DATE_TIME_SHORT.format(timestampTarget.getTimestamp()) + " in channel " + targetChannel.getAsMention() + "\n\n" 
			+ "Delete this bot message to cancel the scheduling message");

			sourceChannel.sendMessage(previewMessageBuilder.build()).queue(msg2 -> {

				long guildID = msg2.getGuild().getIdLong();
				Properties guildMsgs = scheduledMessages.containsKey(guildID) ? scheduledMessages.get(guildID) : new Properties();

				String fileString = timestampString + "|" + targetChannel.getId() + "|" + msg.getContentRaw();

				guildMsgs.put(msg2.getId(), fileString);
				scheduledMessages.put(guildID, guildMsgs);
				saveScheduledMessages(msg2.getGuild(), guildMsgs);

				timerTasks.put(msg2.getIdLong(), task);
			});
		});
	}

	public void scheduleMessage(Guild guild, String fileString, long targetMsgId) {
		String[] split = fileString.split("\\|");
		String timestampString = split[0];
		String targetChannelId = split[1];
		String messageContent = split[2];

		Timestamp timestampTarget = TimeFormat.parse(timestampString);

		Date date = new Date(timestampTarget.getTimestamp());

		if (date.compareTo(Date.from(Instant.now())) < 0) {
			LOGGER.warn("Tried to readded {} to the timer list but the scheduled time is already over: {}", targetMsgId, date.toString());
			Properties prop = scheduledMessages.get(guild.getIdLong());
			prop.remove(Long.toString(targetMsgId));
			saveScheduledMessages(guild, prop);
			return;
		}

		LOGGER.info("Readded {} to the timer list which is scheduled to be sent at {}", targetMsgId, date.toString());

		MessageChannel targetChannel = (MessageChannel) guild.getGuildChannelById(targetChannelId);

		TimerTask task = new TimerTask() {

			@Override
			public void run() {
				try {
					targetChannel.sendMessageEmbeds(MD2Embed.parseEmbed(messageContent, TASCompBot.color).build()).queue();
					;
				} catch (Exception e) {
					e.printStackTrace();
				}

				HashMap<Long, TimerTask> temporaryHashmap = new HashMap<>(timerTasks);
				temporaryHashmap.forEach((msgId, task) -> {
					if (task.equals(this)) {
						timerTasks.remove(msgId);
						scheduledMessages.remove(msgId);
					}
				});
			}
		};
		timer.schedule(task, date);

		timerTasks.put(targetMsgId, task);
	}

	public void onDelete(Guild guild, long msgId) {
		if (timerTasks.containsKey(msgId)) {
			LOGGER.info("Removing scheduled message {}", msgId);
			timerTasks.get(msgId).cancel();
			timerTasks.remove(msgId);
			Properties prop = scheduledMessages.get(guild.getIdLong());
			prop.remove(Long.toString(msgId));
			saveScheduledMessages(guild, prop);
		}
	}
}
