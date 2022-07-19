package com.minecrafttas.tascomp;

import org.slf4j.Logger;

import net.dv8tion.jda.api.entities.Guild;

public class SubmissionHandler {
	
	private Logger logger;
	
	
	public SubmissionHandler(Logger logger) {
		this.logger=logger;
	}
	
	public void prepareGuild(Guild guild) {
		//Mkdir stuff
	}
	
	public void submit(Guild guild, String raw, String author, long id) {
		
	}
	
	public void clearSubmissions(Guild guild) {
		
	}
}
