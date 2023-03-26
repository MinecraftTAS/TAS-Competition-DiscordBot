package com.minecrafttas.tascomp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.Storable;
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

public class SubmissionHandler extends Storable {

	private Logger LOGGER;

	public SubmissionHandler(Logger logger) {
		super("submissions", new File("submissions/"), logger);
		this.LOGGER = logger;
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

		String authorId = author.getId();

		GuildConfigs config = TASCompBot.getBot().getGuildConfigs();

		String submitChannelID = config.getValue(guild, ConfigValues.SUBMITCHANNEL);
		MessageChannel submitChannel = null;
		if (submitChannelID == null) {
			return;
		}
		submitChannel = (MessageChannel) guild.getGuildChannelById(submitChannelID);

		Properties guildSubmission = guildProperties.containsKey(guildID) ? guildProperties.get(guildID) : new Properties();

		boolean flag = guildSubmission.containsKey(authorId);

		if (flag) {
			String submission = guildSubmission.getProperty(authorId);
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

			guildSubmission.put(authorId, value);
			save(guild, guildSubmission);

			guildProperties.put(guild.getIdLong(), guildSubmission);

			String replyText = flag ? "Your submission was updated!" : "Your submission was saved!";

			if (dm) {
				Util.sendDeletableDirectMessage(author, replyText);
			}
		});
	}

	public void clearSubmission(GenericCommandInteractionEvent event, User author) {
		Properties guildSubmission = guildProperties.get(event.getGuild().getIdLong());
		guildSubmission.remove(author.getId());
		save(event.getGuild(), guildSubmission);
		Util.sendDeletableReply(event, "Cleared submission of " + author.getAsTag());
	}

	public void clearAllSubmissions(GenericCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		remove(guild);
		if (guildProperties.get(guild.getIdLong()) != null) {
			guildProperties.remove(guild.getIdLong());
		} else {
			Util.sendDeletableReply(event, "Nothing to clear!");
			return;
		}
		Util.sendDeletableReply(event, "Cleared all submissions!");
	}

	public void sendSubmissionList(GenericCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		if (guildProperties.get(guild.getIdLong()) == null) {
			Util.sendDeletableReply(event, "Submission list is empty!");
			return;
		}
		
		List<MessageEmbed> embeds = getSubmissionList(guild);
		if (embeds.isEmpty()) {
			Util.sendDeletableReply(event, "Submission list is empty!");
			return;
		}
		
		for(MessageEmbed embed: embeds) {
			MessageCreateBuilder builder = new MessageCreateBuilder().setEmbeds(embed);
			Util.sendDeletableReply(event, builder.build());
		}
	}

	private List<MessageEmbed> getSubmissionList(Guild guild) {
		Properties submission = guildProperties.get(guild.getIdLong());
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
		Properties guildSubmission = guildProperties.get(guild.getIdLong());
		if(guildSubmission==null) {
			return false;
		}
		return guildSubmission.containsKey((Object)author.getId());
	}
}
