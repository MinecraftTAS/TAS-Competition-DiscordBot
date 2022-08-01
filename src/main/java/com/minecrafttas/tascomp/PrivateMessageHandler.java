package com.minecrafttas.tascomp;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.Util;
import com.vdurmont.emoji.EmojiManager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public class PrivateMessageHandler {
	
	private static Logger LOGGER;
	
	private final Emoji singleGuildEmoji=Emoji.fromUnicode(EmojiManager.getForAlias("incoming_envelope").getUnicode());

	private SubmissionHandler submissionHandler;

	private GuildConfigs guildConfigs;
	
	private List<String> multiParticipationWarning = new ArrayList<>();
	
	public PrivateMessageHandler(Logger logger, SubmissionHandler submissionHandler, GuildConfigs guildConfigs) {
		LOGGER=logger;
		LOGGER.info("Preparing private message handler...");
		this.submissionHandler=submissionHandler;
		this.guildConfigs=guildConfigs;
	}
	
	public void setupReactions(Message message) {
		
		List<Guild> participationGuilds = PrivateMessageHandler.getActiveParticipationGuilds(message.getAuthor());
		
		if (participationGuilds.size() == 1) {

			message.addReaction(singleGuildEmoji).queue();

		} else if (participationGuilds.size() > 1 && participationGuilds.size() < 10) {
			
			if(!multiParticipationWarning.contains(message.getAuthor().getAsTag())) {
				multiParticipationWarning.add(message.getAuthor().getAsTag());
				sendActiveCompetitions(message.getAuthor());
			}
			
			for (int i = 1; i <= participationGuilds.size(); i++) {
				message.addReaction(intToEmoji(i)).queue();
			}
		}
	}
	
	public void processPrivateReactions(Message messsage, Emoji reactionEmote, User dmUser) {
		
		// React with a envelope when participation guild, react with more if multiple
		Guild participationGuild = getParticipationGuild(messsage, reactionEmote);
		
		if(participationGuild==null) {
			return;
		}
		
		//Submit command
		String submit = MD2Embed.matchAndGet("^!submit (.+)", messsage.getContentRaw(), 1);
		
		if(submit!=null) {
			
			if(TASCompBot.getBot().isCompetitionRunning(participationGuild)) {
				User user = messsage.getAuthor();
				submissionHandler.submit(participationGuild, user, messsage, submit);
			}
			
		} else {
			// If no command was in the message
			if(!guildConfigs.hasValue(participationGuild, ConfigValues.ORGANIZERCHANNEL)) {
				Util.sendDeletableDirectMessage(dmUser, "The destination channel for "+participationGuild.getName()+" was not set by their admins. You may alert them of this mistake...");
				return;
			}
			MessageChannel channel = (MessageChannel) participationGuild
					.getGuildChannelById(guildConfigs.getValue(participationGuild, ConfigValues.ORGANIZERCHANNEL));
			
			
			Util.sendMessage(channel, Util.constructMessageWithAuthor(messsage));
			
		}
		
		messsage.removeReaction(reactionEmote).queue();
		Emoji envelope = Emoji.fromUnicode(EmojiManager.getForAlias(":envelope:").getUnicode());
		messsage.addReaction(envelope).queue();
	}
	
	public static List<Guild> getActiveParticipationGuilds(User userIn) {
		
		List<Guild> guilds = TASCompBot.getBot().getJDA().getGuilds();
		List<Guild> participateGuilds = new ArrayList<>();
		for (Guild guild : guilds) {
			String roleID = TASCompBot.getBot().getGuildConfigs().getValue(guild, ConfigValues.PARTICIPATEROLE);
			boolean isRunning = TASCompBot.getBot().getGuildConfigs().getValue(guild, ConfigValues.COMPETITION_RUNNING).equals("true");
			if (roleID == null || !isRunning) {
				continue;
			}
			Role role = guild.getRoleById(roleID);
			Member member = guild.getMemberById(userIn.getIdLong());
			if (member.getRoles().contains(role)) {
				participateGuilds.add(guild);
			}
		}
		return participateGuilds;
	}
	
	private Guild getParticipationGuild(Message messsage, Emoji reactionEmote) {
		// Get participation guilds
		List<Guild> participationGuilds = getActiveParticipationGuilds(messsage.getAuthor());
		
		if (participationGuilds.size() == 1 && reactionEmote.equals(singleGuildEmoji)) {
			
			return participationGuilds.get(0);
			
		} else if (participationGuilds.size() > 1 && participationGuilds.size() < 10 && hasNumberEmoji(reactionEmote.getFormatted())) {
			int channelNumber = 0;
			if(EmojiManager.containsEmoji(reactionEmote.getFormatted())) {
				
				String emoji=reactionEmote.getFormatted();
				
				channelNumber = unicodeToInt(emoji);
			}
			return participationGuilds.get(channelNumber-1);
		} else {
			return null;
		}
	}

	public void sendActiveCompetitions(User user) {
		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle("Active TAS Competitions");
		builder.setDescription("This list shows you all TAS Competitions that you are currently participating in and the emoji you need react in order to submit something.\n\n"
				+ "If there is only one server you are participating in, the bot will react with a \uD83D\uDCE8. Reacting to that will forward the message to the organizers.\n\n"
				+ "If you participate in multiple servers, the server will react with 1\uFE0F\u20E3, 2\uFE0F\u20E3, 3\uFE0F\u20E3 etc... Use this list to look up the correct server\n\n"
				+ "`!servers` will display this info again");
		List<Guild> guilds = PrivateMessageHandler.getActiveParticipationGuilds(user);
		int i=1;
		for (Guild guild : guilds) {
			builder.addField("", i+"\uFE0F\u20E3 "+guild.getName(), false);
			i++;
		}
		builder.setColor(TASCompBot.color);
		Message msg = new MessageBuilder(builder).build();
		Util.sendDeletableDirectMessage(user, msg);
	}
	
	public static int emojiToInt(Emoji emoji) {
		return unicodeToInt(emoji.getFormatted());
	}
	
	public static int unicodeToInt(String emoji) {
		switch (emoji) {
		case "1\uFE0F\u20E3":
			return 1;
		case "2\uFE0F\u20E3":
			return 2;
		case "3\uFE0F\u20E3":
			return 3;
		case "4\uFE0F\u20E3":
			return 4;
		case "5\uFE0F\u20E3":
			return 5;
		case "6\uFE0F\u20E3":
			return 6;
		case "7\uFE0F\u20E3":
			return 7;
		case "8\uFE0F\u20E3":
			return 8;
		case "9\uFE0F\u20E3":
			return 9;
		default:
			return 0;
		}
	}

	public static Emoji intToEmoji(int i) {
		return Emoji.fromUnicode(intToUnicode(i));
	}
	
	public static String intToUnicode(int i) {
		switch (i) {
		case 1:
			return EmojiManager.getForAlias("one").getUnicode();
		case 2:
			return EmojiManager.getForAlias("two").getUnicode();
		case 3:
			return EmojiManager.getForAlias("three").getUnicode();
		case 4:
			return EmojiManager.getForAlias("four").getUnicode();
		case 5:
			return EmojiManager.getForAlias("five").getUnicode();
		case 6:
			return EmojiManager.getForAlias("six").getUnicode();
		case 7:
			return EmojiManager.getForAlias("seven").getUnicode();
		case 8:
			return EmojiManager.getForAlias("eight").getUnicode();
		case 9:
			return EmojiManager.getForAlias("nine").getUnicode();
		default:
			return null;
		}
	}
	
	public static boolean hasNumberEmoji(Emoji emoji) {
		return hasNumberEmoji(emoji.getFormatted());
	}
	
	public static boolean hasNumberEmoji(String emoji) {
		switch (emoji) {
		case "1\uFE0F\u20E3":
			return true;
		case "2\uFE0F\u20E3":
			return true;
		case "3\uFE0F\u20E3":
			return true;
		case "4\uFE0F\u20E3":
			return true;
		case "5\uFE0F\u20E3":
			return true;
		case "6\uFE0F\u20E3":
			return true;
		case "7\uFE0F\u20E3":
			return true;
		case "8\uFE0F\u20E3":
			return true;
		case "9\uFE0F\u20E3":
			return true;
		default:
			return false;
		}
	}
}
