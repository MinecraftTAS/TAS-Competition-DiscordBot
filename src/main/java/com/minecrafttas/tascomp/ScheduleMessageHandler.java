package com.minecrafttas.tascomp;

import java.io.File;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;

import com.minecrafttas.tascomp.util.MD2Embed;
import com.minecrafttas.tascomp.util.Storable;
import com.minecrafttas.tascomp.util.Util;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.Timestamp;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

public class ScheduleMessageHandler extends Storable{

	private Logger LOGGER;

	private final HashMap<Long, TimerTask> timerTasks = new HashMap<>();

	private static Timer timer = new Timer("Schedule Message Timer");

	public ScheduleMessageHandler(Logger logger) {
		super("Scheduled Messages", new File("scheduled/"), logger);
		LOGGER = logger;
	}

	@Override
	public void loadForGuild(Guild guild) {
		Properties prop = new Properties();

		File submissionFile = new File(storageDir, guild.getId() + ".xml");
		if (submissionFile.exists()) {
			prop = load(guild, submissionFile);
			
			Properties tempProp = (Properties) prop.clone();
			
			guildProperties.put(guild.getIdLong(), prop);
			tempProp.forEach((target, fileString) -> {
				long targetId = Long.parseLong((String) target);
				scheduleMessage(guild, (String) fileString, targetId);
			});
			save(guild, prop);
		}
	}

	public void scheduleMessage(Guild guild, MessageChannel sourceChannel, User author, String messageId, MessageChannel targetChannel, String timestampString) throws Exception {
		sourceChannel.retrieveMessageById(messageId).queue(msg -> {

			Timestamp timestampTarget = TimeFormat.parse(timestampString);

			Date date = new Date(timestampTarget.getTimestamp());

			if (date.compareTo(Date.from(Instant.now())) < 0) {
				LOGGER.warn("{{}} Tried to add scheduled message but the scheduled time is already over: {}", guild.getName(), date.toString());
				Util.sendSelfDestructingMessage(sourceChannel, "Can't add a scheduled message. The timestamp lies in the past!", 10);
				return;
			}

			TimerTask task = new TimerTask() {

				@Override
				public void run() {
					try {
						targetChannel.sendMessage(MD2Embed.parseMessage(msg, TASCompBot.color).build()).queue();
					} catch (Exception e) {
						e.printStackTrace();
					}

					HashMap<Long, TimerTask> temporaryHashmap = new HashMap<>(timerTasks);
					temporaryHashmap.forEach((msgId, task) -> {
						if (task.equals(this)) {
							timerTasks.remove(msgId);
							guildProperties.remove(msgId);
							removeFromProperties(guild, msgId);
						}
					});
				}
			};
			timer.schedule(task, date);

			MessageCreateBuilder previewMessageBuilder = null;
			try {
				previewMessageBuilder = MD2Embed.parseMessage(msg, TASCompBot.color);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			String messageContent = previewMessageBuilder.getContent();
			String messageRaw = MD2Embed.parseMessageAsString(msg);

			previewMessageBuilder.setContent(author.getAsMention()+" Scheduled message to " + TimeFormat.DATE_TIME_SHORT.format(timestampTarget.getTimestamp()) + " in channel " + targetChannel.getAsMention() + "\n\n" 
			+ "Delete this bot message to cancel the message scheduling\n----------------------------------------------\n"+messageContent);

			sourceChannel.sendMessage(previewMessageBuilder.build()).queue(msg2 -> {

				long guildID = msg2.getGuild().getIdLong();
				Properties guildMsgs = guildProperties.containsKey(guildID) ? guildProperties.get(guildID) : new Properties();

				String fileString = timestampString + "|" + targetChannel.getId() + "|" + messageRaw;

				guildMsgs.put(msg2.getId(), fileString);
				guildProperties.put(guildID, guildMsgs);
				save(msg2.getGuild(), guildMsgs);

				timerTasks.put(msg2.getIdLong(), task);
				msg2.addReaction(Util.deletableEmoji).queue();
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
			LOGGER.warn("{{}} Tried to readded {} to the timer list but the scheduled time is already over: {}", guild.getName(), targetMsgId, date.toString());
			Properties prop = guildProperties.get(guild.getIdLong());
			prop.remove(Long.toString(targetMsgId));
			return;
		}

		LOGGER.info("{{}} Readded {} to the timer list which is scheduled to be sent at {}", guild.getName(), targetMsgId, date.toString());

		MessageChannel targetChannel = (MessageChannel) guild.getGuildChannelById(targetChannelId);

		TimerTask task = new TimerTask() {

			@Override
			public void run() {
				try {
					targetChannel.sendMessage(MD2Embed.parseMessage(messageContent, TASCompBot.color).build()).queue();
				} catch (Exception e) {
					e.printStackTrace();
				}

				HashMap<Long, TimerTask> temporaryHashmap = new HashMap<>(timerTasks);
				temporaryHashmap.forEach((msgId, task) -> {
					if (task.equals(this)) {
						timerTasks.remove(msgId);
						guildProperties.remove(msgId);
						removeFromProperties(guild, targetMsgId);
					}
				});
			}
		};
		timer.schedule(task, date);

		timerTasks.put(targetMsgId, task);
	}

	public void onDelete(Guild guild, long msgId) {
		if (timerTasks.containsKey(msgId)) {
			LOGGER.info("{{}} Removing scheduled message {}", guild.getName(), msgId);
			timerTasks.get(msgId).cancel();
			timerTasks.remove(msgId);
			removeFromProperties(guild, msgId);
		}
	}
	
	private void removeFromProperties(Guild guild, long msgId) {
		Properties prop = guildProperties.get(guild.getIdLong());
		prop.remove(Long.toString(msgId));
		save(guild, prop);
	}
}
