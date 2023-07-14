package com.minecrafttas.tascomp.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
		
		for(Attachment attachment: message.getAttachments()) {
			messageString+=String.format("\n{%s:%s:%s}", attachment.getContentType(), attachment.getFileName(), attachment.getUrl());
		}
		
		MessageCreateBuilder builder = parseMessage(messageString, color);
		
		return builder;
	}
	
	public static String parseMessageAsString(Message message) {
		String messageString = message.getContentRaw();
		
		for(Attachment attachment : message.getAttachments()) {
			messageString+=String.format("\n{%s:%s:%s}", attachment.getContentType(), attachment.getFileName(), attachment.getUrl());
		}
		
		return messageString;
	}
	
	public static MessageCreateBuilder parseMessage(String messageString, int color) throws Exception {
		String[] lines = messageString.split("\n");
		
		boolean insideBlock=false;
		
		List<String> messageLines = new ArrayList<>();
		
		List<String> embedString = new ArrayList<>();
		List<EmbedBuilder> embeds = new ArrayList<>();
		
		boolean createNewEmbedForImage = true;
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
					createNewEmbedForImage = false;
					continue;
				}
			}
			else if(line.matches("\\{(.+):(.+):(.+)\\}")) {
				String type = matchAndGet("\\{(.+?):(.+?):(.+?)\\}", line, 1);
				String filename = matchAndGet("\\{(.+?):(.+?):(.+?)\\}", line, 2);
				String url = matchAndGet("\\{(.+?):(.+?):(.+?)\\}", line, 3);
				if(type.startsWith("image/") && !createNewEmbedForImage) {
					createNewEmbedForImage = true;
					embeds.get(embeds.size()-1).setUrl("https://minecrafttas.com").setImage(url);
				} else if(type.startsWith("image/") && createNewEmbedForImage){
					embeds.add(new EmbedBuilder().setUrl("https://minecrafttas.com").setImage(url));
				} else {
					embeds.add(new EmbedBuilder().setTitle(filename, url));
				}
				continue;
			}
			
			if (insideBlock) {
				embedString.add(line);
			} else {
				messageLines.add(line);
			}
		}
		
		MessageCreateBuilder builder = new MessageCreateBuilder();
		
		builder.setContent(String.join("\n", messageLines));
		
		Iterator<EmbedBuilder> iterator = embeds.iterator();
		while(iterator.hasNext()) {
			EmbedBuilder embed = iterator.next();
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
