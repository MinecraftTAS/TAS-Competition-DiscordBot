package com.minecrafttas.tascomp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.minecrafttas.tascomp.TASCompBot;
import com.vdurmont.emoji.EmojiManager;

/**
 * A wrapper/helper for discord emojis and emotes.
 * 
 * @author Scribble
 *
 */
public class EmoteUtil {

	/**
	 * The regex for the emote.
	 */
	private static final Pattern pattern = Pattern.compile("<a?:.*:(\\d+)>");

	
	public static boolean isCustom(String idIn) {
		return pattern.matcher(idIn).find();
	}
	
	/**
	 * Checks if an emote is available to the bot
	 * @param emoteId Emote id to check
	 * @return If the emote is available to the bot
	 */
	public static boolean isEmoteAvailable(String emoteId) {
		if (!EmojiManager.isEmoji(emoteId)) {
			return TASCompBot.getBot().getJDA().getEmojiById(extractId(emoteId)) != null;
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
}
