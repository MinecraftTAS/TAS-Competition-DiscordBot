package com.minecrafttas.tascomp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.minecrafttas.tascomp.TASCompBot;
import com.vdurmont.emoji.EmojiManager;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;

/**
 * A wrapper/helper for discord emojis and emotes.
 * 
 * @author Scribble
 *
 */
public class EmoteWrapper {

	private boolean isUnicode;

	private String id = "";

	/**
	 * The regex for the emote.
	 */
	private static final Pattern pattern = Pattern.compile("<a?:.*:(\\d+)>");

	private String printName = "";

	public EmoteWrapper(String idIn) {

		if (!isEmoteAvailable(idIn)) {
			throw new IllegalArgumentException(String.format("The bot can't access this emote: %s", idIn));
		}

		if (EmojiManager.isEmoji(idIn)) {
			isUnicode = true;
			id = idIn;
			printName = idIn;
		} else {
			id = extractId(idIn);
			printName = idIn;
		}
	}

	public boolean isUnicode() {
		return isUnicode;
	}

	/**
	 * Returns either the unicode or the id of the emote
	 * @return
	 */
	public String getId() {
		if (isUnicode()) {
			return id;
		} else {
			return TASCompBot.getBot().getJDA().getEmoteById(id).getId();
		}
	}

	
	@Override
	public String toString() {
		return printName;
	}

	/**
	 * Checks if an emote is available to the bot
	 * @param emoteId Emote id to check
	 * @return If the emote is available to the bot
	 */
	public static boolean isEmoteAvailable(String emoteId) {
		if (!EmojiManager.isEmoji(emoteId)) {
			return TASCompBot.getBot().getJDA().getEmoteById(extractId(emoteId)) != null;
		} else {
			return true;
		}
	}

	/**
	 * Extracts the raw id from custom and animated emotes
	 * @param idIn a:endportal1:968224841473855549
	 * @return 968224841473855549
	 */
	private static String extractId(String idIn) {
		Matcher matcher = pattern.matcher(idIn);
		if (matcher.find()) {
			return matcher.group(1);
		} else {
			return "";
		}
	}
	
	/**
	 * Returns the id from this reaction emote
	 * @param emoteIn The Reaction emote
	 * @return If the reaction emote is an emoji, returns the unicode, else the emote id
	 */
	public static String getReactionEmoteId(ReactionEmote emoteIn) {
		if(emoteIn.isEmoji()) {
			return emoteIn.getEmoji();
		}
		else if (emoteIn.isEmote()) {
			return emoteIn.getEmote().getId();
		}
		return "";
	}
	
	public Emote getEmote() {
		if(!isUnicode) {
			return TASCompBot.getBot().getJDA().getEmoteById(id);
		}
		return null;
	}
}
