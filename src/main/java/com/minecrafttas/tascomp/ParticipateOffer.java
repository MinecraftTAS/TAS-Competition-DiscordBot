package com.minecrafttas.tascomp;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.MD2Embed;
import com.minecrafttas.tascomp.util.Storable;
import com.minecrafttas.tascomp.util.Util;
import com.minecrafttas.tascomp.util.WarpedImage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class ParticipateOffer extends Storable{
	private static Timer timer= new Timer();
	private HashMap<Long, Offer> offerList=new HashMap<>();
	private GuildConfigs configs;
	private Logger LOGGER;
	
	public ParticipateOffer(GuildConfigs configs, Logger logger) {
		super("Participate Blacklist", new File("blacklist"), logger);
		this.LOGGER = logger;
		this.configs = configs;
		timer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				offerList.values().removeIf(offer-> {
					if(offer.isExpired()) {
						LOGGER.info("{{}} Request expired for user {}", offer.getGuild().getName(), offer.getUser().getName());
						Util.sendSelfDestructingDirectMessage(offer.getUser(), String.format("Your request expired! You may try again by using /participate in %s", offer.getGuild().getName()), 20);
					}
					return offer.isExpired();
				});
			}
			
		}, 100, TimeUnit.MINUTES.toMillis(1));
	}
	
	public void startOffer(Guild guild, User user) throws Exception {
		
		String code = generateRandomText();
		LOGGER.info("{{}} Start offering to {} with code {}", guild.getName(), user.getName(), code);
		EmbedBuilder embed = MD2Embed.parseEmbed(configs.getValue(guild, ConfigValues.RULEMSG), TASCompBot.color);
		embed.addField("Accepting:", "To accept, write `!accept <code in the image>`", false);
		embed.setImage("attachment://captcha.png");
		MessageCreateData msg = new MessageCreateBuilder().setEmbeds(embed.build()).build();
		user.openPrivateChannel().queue(channel ->{
			FileUpload upload = FileUpload.fromData(WarpedImage.makeCaptcha(code), "captcha.png");
			channel.sendMessage(msg).addFiles(upload).queue(msg2->msg2.addReaction(Util.deletableEmoji).queueAfter(5, TimeUnit.MINUTES));
		});
		
		offerList.put(user.getIdLong(), new Offer(guild, user, code));
	}
	
	public Guild checkCode(User user, String code) {
		if(offerList.containsKey(user.getIdLong())) {
			Offer offer = offerList.get(user.getIdLong());
			if(code.equals(offer.getCode())) {
				offerList.remove(user.getIdLong());
				return offer.getGuild();
			}else
				return null;
		}else
			return null;
		
	}
	
	public boolean isOnBlacklist(Guild guild, User user) {
		Properties prop = guildProperties.get(guild.getIdLong());
		if(prop == null) {
			return false;
		}
		return prop.containsKey(user.getId());
	}
	
	public void addToBlacklist(GenericCommandInteractionEvent event, User user) {
		Guild guild = event.getGuild();
		Properties prop = guildProperties.get(guild.getIdLong());
		
		prop = prop!=null? prop : new Properties();
		
		if(!prop.containsKey(user.getId())) {
			LOGGER.info("{{}} Adding user {} to the blacklist", guild.getName(), user.getAsTag());
			prop.put(user.getId(), user.getAsTag());
			guildProperties.put(guild.getIdLong(), prop);
			save(guild, prop);
			Util.sendReply(event, String.format("Added user %s to the blacklist. They can no longer use /participate", user.getAsTag()), true);
		} else {
			Util.sendReply(event, String.format("The user %s is already on the blacklist.", user.getAsTag()), true);
		}
	}
	
	public void removeFromBlacklist(GenericCommandInteractionEvent event, User user) {
		Guild guild = event.getGuild();
		Properties prop = guildProperties.get(guild.getIdLong());
		
		if(prop == null) {
			Util.sendReply(event, "The blacklist is empty!", true);
			return;
		}
		
		if(prop.containsKey(user.getId())) {
			LOGGER.info("{{}} Removing user {} from the blacklist", guild.getName(), user.getAsTag());
			prop.remove(user.getId());
			guildProperties.put(guild.getIdLong(), prop);
			save(guild, prop);
			Util.sendReply(event, String.format("Removed user %s from the blacklist. They can use /participate again", user.getAsTag()), true);
		} else {
			Util.sendReply(event, String.format("The user %s is not on the blacklist.", user.getAsTag()), true);
		}
	}
	
	public void clearBlacklist(GenericCommandInteractionEvent event) {
		Guild guild = event.getGuild();
		Properties prop = guildProperties.get(guild.getIdLong());
		
		if(prop == null) {
			Util.sendReply(event, "The blacklist is empty!", true);
			return;
		}
		LOGGER.info("{{}} Clearing the blacklist.", guild.getName());
		remove(guild);
		Util.sendReply(event, "Cleared the blacklist!", true);
	}
	
	private class Offer{
		
		private long startTime=System.currentTimeMillis();
		private Guild guild;
		private User user;
		private String code;
		
		public Offer(Guild guild, User user, String code) {
			this.guild=guild;
			this.user=user;
			this.code=code;
		}
		
		public boolean isExpired() {
			return System.currentTimeMillis()-startTime >= TimeUnit.MINUTES.toMillis(5);
		}
		
		public User getUser() {
			return user;
		}
		
		public String getCode() {
			return code;
		}
		
		public Guild getGuild() {
			return guild;
		}
	}
	
	private String generateRandomText() {
	    int length = 5;
	    boolean useLetters = true;
	    boolean useNumbers = true;
	    String generatedString = RandomStringUtils.random(length, useLetters, useNumbers);
	    return generatedString;
	}
	
	public boolean isOffering(User user) {
		return offerList.containsKey(user.getIdLong());
	}
}
