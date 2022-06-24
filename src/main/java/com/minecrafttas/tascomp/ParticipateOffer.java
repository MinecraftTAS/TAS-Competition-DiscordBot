package com.minecrafttas.tascomp;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.Util;
import com.minecrafttas.tascomp.util.WarpedImage;
import com.vdurmont.emoji.EmojiManager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

public class ParticipateOffer {
	private static Timer timer= new Timer();
	private HashMap<Long, Offer> offerList=new HashMap<>();
	private GuildConfigs configs;
	
	public ParticipateOffer(GuildConfigs configs) {
		this.configs = configs;
		timer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				offerList.values().removeIf(offer-> {
					if(offer.isExpired()) {
						Util.sendSelfDestructingDirectMessage(offer.getUser(), "Your request expired!", 20);
					}
					return offer.isExpired();
				});
			}
			
		}, 100, TimeUnit.MINUTES.toMillis(1));
	}
	
	public void startOffer(Guild guild, User user) throws Exception {
		String code = generateRandomText();
		System.out.println(code);
		EmbedBuilder embed=MD2Embed.parseEmbed(configs.getValue(guild, ConfigValues.RULEMSG), TASCompBot.color);
		embed.addField("Accepting:", "To accept, write `!accept <code in the image>`", false);
		embed.setImage("attachment://captcha.png");
		Message msg = new MessageBuilder(embed).build();
		user.openPrivateChannel().queue(channel ->{
			channel.sendMessage(msg).addFile(WarpedImage.makeCaptcha(code), "captcha.png").queue(msg2-> msg2.addReaction(EmojiManager.getForAlias(":x:").getUnicode()).queue());
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
}
