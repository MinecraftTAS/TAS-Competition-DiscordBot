package com.minecrafttas.tascomp.util;

import java.util.ArrayList;
import java.util.List;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
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
}
