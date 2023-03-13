package com.minecrafttas.tascomp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.EmbedBuilder;

public class MD2Embed {
	
	public static EmbedBuilder parseEmbed(String embedString, int color) throws Exception{
		// TODO Error handling with too many characters
		EmbedBuilder builder = new EmbedBuilder();
		builder.setColor(color);
		String[] lines = embedString.split("\n");
		
		String description=null;
		
		String fieldTitle=null;
		String fieldDescription="";
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			int linenumber = i+1;
			
			try {
				String title = matchAndGet("^# (.*)", line, 1);
				if (title != null) {
					builder.setTitle(title);
					description="";
					continue;
				}
				
				String newfield = matchAndGet("^## (.*)", line, 1);
				if (newfield != null) {
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

				if (line.equals("```")) {
					if (description != null) {
						builder.setDescription(description);
						description = null;
					}
					if (fieldTitle != null) {
						builder.addField(fieldTitle, fieldDescription, false);
						fieldDescription = null;
					}
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
	
	public static String toEmbedString(EmbedBuilder embed) {
		return null;
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
