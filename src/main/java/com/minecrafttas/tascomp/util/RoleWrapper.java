package com.minecrafttas.tascomp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.Guild;

/**
 * Simple wrapper class around a role
 * @author Scribble
 *
 */
public class RoleWrapper {

	private String printText;

	private String id;

	private static final Pattern rolePattern = Pattern.compile("<@&(\\d+)>");

	public RoleWrapper(Guild guild, String roleIdIn) throws IllegalArgumentException {
		if (!isRoleAvailable(guild, roleIdIn)) {
			throw new IllegalArgumentException("The bot can't access this role: " + roleIdIn);
		}

		id = extractRole(roleIdIn);
		printText = roleIdIn;
	}

	/**
	 * The extracted id of the role
	 * @return
	 */
	public String getId() {
		return id;
	}

	/**
	 * The prinatble name of the role
	 * @return The prinatble name of the role
	 */
	@Override
	public String toString() {
		return printText;
	}

	/**
	 * Checks if the specified role is available on the guild
	 * @param guild The guild to check
	 * @param roleIn The role to check
	 * @return If the role is available
	 */
	public static boolean isRoleAvailable(Guild guild, String roleIn) {
		try {
			return guild.getRoleById(extractRole(roleIn)) != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Extracts the id of a role
	 * @param roleIn The unparsed roleId
	 * @return the parsed role id
	 */
	public static String extractRole(String roleIn) {
		Matcher matcher = rolePattern.matcher(roleIn);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return roleIn;
	}
}
