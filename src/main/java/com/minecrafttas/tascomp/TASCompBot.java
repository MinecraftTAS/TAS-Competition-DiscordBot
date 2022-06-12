package com.minecrafttas.tascomp;

import java.util.HashMap;
import java.util.Properties;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class TASCompBot extends ListenerAdapter implements Runnable {

	private final JDA jda;
	private final Properties configuration;
	private final HashMap<Long, Properties> guildConfigs = new HashMap<>();
	
	public TASCompBot(Properties configuration) throws InterruptedException, LoginException {
		this.configuration = configuration;
		final JDABuilder builder = JDABuilder.createDefault(this.configuration.getProperty("token"))
				.setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this);
		this.jda = builder.build();
		this.jda.awaitReady();
	}

	@Override
	public void run() {
		/* Register the Commands */
		System.out.println("[TAS Competition] Preparing Bot...");
		for (Guild guild : jda.getGuilds()) {
			
		}
		System.out.println("[TAS Competition] Done preparing bot.");
	}
}
