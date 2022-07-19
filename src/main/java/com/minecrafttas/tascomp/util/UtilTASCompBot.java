package com.minecrafttas.tascomp.util;

import java.util.ArrayList;
import java.util.List;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.vdurmont.emoji.EmojiManager;
import com.minecrafttas.tascomp.TASCompBot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

public class UtilTASCompBot {

	public static List<Guild> getActiveParticipationGuilds(User userIn) {
		List<Guild> guilds = TASCompBot.getBot().getJDA().getGuilds();
		List<Guild> participateGuilds = new ArrayList<>();
		for (Guild guild : guilds) {
			String roleID = TASCompBot.getBot().getGuildConfigs().getValue(guild, ConfigValues.PARTICIPATEROLE);
			if (roleID == null) {
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
}
