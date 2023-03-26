package com.minecrafttas.tascomp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

public class MD2Embed {
	
	public static EmbedBuilder parseEmbed(String embedString, int color) throws Exception{
		EmbedBuilder builder = new EmbedBuilder();
		builder.setColor(color);
		
		boolean insideBlock=false;
		
		String[] lines = embedString.split("\n");
		
		String description=null;
		
		String fieldTitle=null;
		String fieldDescription="";
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			int linenumber = i+1;
			
			if (line.matches("^```(.+)?")) {
				insideBlock = !insideBlock;
				
				if(!insideBlock) {
					if (description != null) {
						builder.setDescription(description);
						description = null;
					}
					if (fieldTitle != null) {
						builder.addField(fieldTitle, fieldDescription, false);
						fieldDescription = null;
					}
				}
				continue;
			}
			
			if(!insideBlock)
				continue;
			
			try {
				//Title
				String title = matchAndGet("^# (.*)", line, 1);
				if (title != null) {	//Set the title, reset description
					builder.setTitle(title);
					description="";
					continue;
				}
				
				//Field
				String newfield = matchAndGet("^## (.*)", line, 1);
				if (newfield != null) {	//Start the field title, set description, reset field description
					if (description != null) {
						builder.setDescription(description);
						description = null;
					}
					if (fieldTitle != null) {
						builder.addField(fieldTitle, fieldDescription, false);
						fieldDescription = "";
					}
					fieldTitle = newfield;
					continue;
				}

				if (description != null) {
					description = description.concat(line + "\n");
				}

				if (fieldTitle != null) {
					fieldDescription = fieldDescription.concat(line + "\n");
				}
			} catch (Exception e) {
				throw new Exception("Exception parsing message embed in line "+ linenumber+": "+e.getMessage());
			}
		}
		return builder;
	}
	
	public static MessageCreateBuilder parseMessage(Message message, int color) throws Exception {
		String messageString = message.getContentRaw();
		
		for(Attachment attachment : message.getAttachments()) {
			messageString += "\n"+attachment.getUrl();
		}
		
		return parseMessage(messageString, color);
	}
	
	public static String parseMessageAsString(Message message) {
		String messageString = message.getContentRaw();
		
		for(Attachment attachment : message.getAttachments()) {
			messageString += "\n"+attachment.getUrl();
		}
		
		return messageString;
	}
	
	public static MessageCreateBuilder parseMessage(String messageString, int color) throws Exception {
		String[] lines = messageString.split("\n");
		
		boolean insideBlock=false;
		
		List<String> messageLines = new ArrayList<>();
		
		List<String> embedString = new ArrayList<>();
		ConcurrentLinkedQueue<EmbedBuilder> embeds = new ConcurrentLinkedQueue<>();
		
		
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
//			int linenumber = i+1;
			
			if (line.matches("^```(.+)?")) {
				insideBlock = !insideBlock;
				
				if(insideBlock) {
					embedString = new ArrayList<>();
				} else {
					embedString.add(line);
					embeds.add(MD2Embed.parseEmbed(embedString, color));
					continue;
				}
			}

			if (insideBlock) {
				embedString.add(line);
			} else {
				messageLines.add(line);
			}
		}
		
		MessageCreateBuilder builder = new MessageCreateBuilder();
		
		builder.setContent(String.join("\n", messageLines));
		
		EmbedBuilder embed = null;
		
		while((embed = embeds.poll()) != null) {
			builder.addEmbeds(embed.build());
		}
		
		return builder;
	}
	
	public static EmbedBuilder parseEmbed(List<String> lines, int color) throws Exception {
		return MD2Embed.parseEmbed(String.join("\n", lines), color);
	}


	public static String matchAndGet(String pattern, String match, int get) {
		Pattern pat=Pattern.compile(pattern);
		Matcher mat=pat.matcher(match);
		if(mat.find()&&mat.groupCount()>0) {
			return mat.group(get);
		}else {
			return null;
		}
	}
}
