package com.minecrafttas.tascomp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.RoleWrapper;
import com.minecrafttas.tascomp.util.Util;
import com.minecrafttas.tascomp.util.UtilTASCompBot;
import com.vdurmont.emoji.EmojiManager;

import ch.qos.logback.core.util.ContentTypeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;

public class TASCompBot extends ListenerAdapter implements Runnable {

	private static TASCompBot instance;
	private final JDA jda;
	private final Properties configuration;
	private final GuildConfigs guildConfigs;
	private final SubmissionHandler submissionHandler;
	private final ParticipateOffer offer;
	private static final Logger LOGGER = LoggerFactory.getLogger("TAS Competition");
	public static final int color=0x0a8505;
	
	public TASCompBot(Properties configuration) throws InterruptedException, LoginException {
		instance=this;
		this.configuration = configuration;
		final JDABuilder builder = JDABuilder.createDefault(this.configuration.getProperty("token"))
				.setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this);
		this.guildConfigs=new GuildConfigs(LOGGER);
		this.submissionHandler = new SubmissionHandler(LOGGER);
		this.offer= new ParticipateOffer(guildConfigs);
		this.jda = builder.build();
		this.jda.awaitReady();
	}

	@Override
	public void run() {
		/* Register the Commands */
		LOGGER.info("Preparing bot...");
		
		for (Guild guild : jda.getGuilds()) {
			prepareGuild(guild);
		}
		
		LOGGER.info("Done preparing bot!");
	}
	
	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		LOGGER.info("Bot joined new guild {}.", event.getGuild().getName());
		prepareGuild(event.getGuild());
	}
	
	private void prepareGuild(Guild guild) {
		LOGGER.info("Preparing guild {}...", guild.getName());
		guild.loadMembers();
		prepareCommands(guild);
		guildConfigs.prepareConfig(guild);
		submissionHandler.loadSubmissionsForGuild(guild);
		
		LOGGER.info("Done preparing guild {}!", guild.getName());
	}

	private void prepareCommands(Guild guild) {
		LOGGER.info("Preparing commands...");
		CommandListUpdateAction updater = guild.updateCommands();
		
		/*Important! The subcommands name(e.g. organizerchannel, participaterole) are made so it matches (ignoring capitalization) 
		 * the config name in GuildConfigs (ConfigValues.ORGANIZERCHANNEL, ConfigValues.PARTICIPATEROLE).
		 * This makes it easier to set the config values*/
		
		// =========================Set Channel
		// /setchannel
		CommandDataImpl setChannelCommand = new CommandDataImpl("setchannel", "Set's the current channel to the bots channel");
		setChannelCommand.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		
		// /setchannel add
		SubcommandGroupData addSubCommandGroupChannel = new SubcommandGroupData("add", "Adds the channel to the config");
		// /setchannel remove
		SubcommandGroupData removeSubCommandGroupChannel = new SubcommandGroupData("remove", "Removes the channel from the config");
		
		// /setchannel add/remove <organizerchannel|...|...>
		SubcommandData[] setChannelSubcommands= {
				new SubcommandData("organizerchannel", "The channel where dm's to the bot will get forwarded to"), 
				new SubcommandData("submitchannel", "The channel where tas submissions will get forwarded to"),
				new SubcommandData("participatechannel", "The channel where participants can get the participant role")
				};
		
		// Add subcommands to subcommand groups
		addSubCommandGroupChannel.addSubcommands(setChannelSubcommands);
		removeSubCommandGroupChannel.addSubcommands(setChannelSubcommands);
		
		// /setchannel list
		SubcommandData listSubCommand = new SubcommandData("list", "Lists the current settings for this command");
		
		// Add everything to the command
		setChannelCommand.addSubcommands(listSubCommand);
		setChannelCommand.addSubcommandGroups(addSubCommandGroupChannel, removeSubCommandGroupChannel);
		
		// =========================TAS Competition Start/Stop
		CommandDataImpl tascompCommand = new CommandDataImpl("tascompetition", "Starts/Stops the TAS Competition");
		tascompCommand.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		
		SubcommandData[] tascompSubcommands= {
				new SubcommandData("start", "Starts a TAS Competition in this guild"),
				new SubcommandData("stop", "Stops a running TAS Competition in this guild")
		};
		tascompCommand.addSubcommands(tascompSubcommands);
		
		// =========================Set Role
		CommandDataImpl setRoleCommand = new CommandDataImpl("setrole", "Set's roles for this guild");
		setRoleCommand.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		
		SubcommandGroupData addSubCommandGroupRole = new SubcommandGroupData("add", "Adds the role to the config");
		SubcommandGroupData removeSubCommandGroupRole = new SubcommandGroupData("remove", "Removes the role from the config");
		
		OptionData roleOption = new OptionData(OptionType.ROLE, "role", "The role in question");
		roleOption.setRequired(true);
		
		SubcommandData[] setRoleSubcommandsNoOption= {
				new SubcommandData("participaterole", "The role for the participants"),
				new SubcommandData("organizerrole", "The role for the organizers")
		};
		
		SubcommandData[] setRoleSubcommands=new SubcommandData[setRoleSubcommandsNoOption.length];
		int i=0;
		for (SubcommandData subcommandData : setRoleSubcommandsNoOption) {
			setRoleSubcommands[i]=new SubcommandData(subcommandData.getName(), subcommandData.getDescription()).addOptions(roleOption);
			i++;
		}
		
		addSubCommandGroupRole.addSubcommands(setRoleSubcommands);
		removeSubCommandGroupRole.addSubcommands(setRoleSubcommandsNoOption);
		
		setRoleCommand.addSubcommands(listSubCommand);
		setRoleCommand.addSubcommandGroups(addSubCommandGroupRole, removeSubCommandGroupRole);
		
		//=========================== Preview
		CommandDataImpl previewCommand = new CommandDataImpl("preview", "Previews the embed from a markdown message");
		previewCommand.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		
		OptionData messageIDOption = new OptionData(OptionType.STRING, "messageid", "The message id to preview");
		messageIDOption.setRequired(true);
		previewCommand.addOptions(messageIDOption);
		
		//=========================== SetRule
		CommandDataImpl setRuleCommand = new CommandDataImpl("setrulemessage", "Set's the rule message sent after typing /participate");
		setRuleCommand.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		
		setRuleCommand.addOptions(messageIDOption);
		
		//=========================== Participate
		CommandDataImpl participateCommand = new CommandDataImpl("participate", "Participate in the Minecraft TAS Competition!");
		participateCommand.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		
		//=========================== Submit
		CommandDataImpl submitCommand = new CommandDataImpl("submit", "Controls submissions");
		submitCommand.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		
		SubcommandData submitAddSubCommand=new SubcommandData("add", "Manually adds a submission");
		OptionData userOption = new OptionData(OptionType.USER, "user", "The user of this submission");
		userOption.setRequired(true);
		OptionData submissionOption = new OptionData(OptionType.STRING, "submission", "The submission");
		submissionOption.setRequired(true);
		submitAddSubCommand.addOptions(userOption, submissionOption);
		
		SubcommandData submitClearSubCommand=new SubcommandData("clear", "Manually clears a submission");
		submitClearSubCommand.addOptions(userOption);
		
		SubcommandData submitClearAllSubCommand=new SubcommandData("clearall", "Clears all submissions");
		SubcommandData submitShowAllSubCommand=new SubcommandData("showall", "Shows all submissions");
		
		submitCommand.addSubcommands(submitAddSubCommand, submitClearSubCommand, submitClearAllSubCommand, submitShowAllSubCommand);
		
		
		updater.addCommands(tascompCommand, setChannelCommand, setRoleCommand, previewCommand, setRuleCommand, participateCommand, submitCommand);
		updater.queue();
		LOGGER.info("Done preparing commands!");
	}
	
	private boolean shouldExecuteParticipate(Guild guild) {
		return isCompetitionRunning(guild) && 
				guildConfigs.hasValue(guild, ConfigValues.PARTICIPATECHANNEL) && 
				guildConfigs.hasValue(guild, ConfigValues.PARTICIPATEROLE) && 
				guildConfigs.hasValue(guild, ConfigValues.RULEMSG);
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		LOGGER.info("{}: Running slash command {} in {}", event.getUser().getAsTag(), event.getCommandPath(), event.getGuild().getName());
		event.deferReply().queue(hook -> {
			try {
				String commandPath = event.getCommandPath();

				// ================== TAS Competition Command
				if(commandPath.startsWith("tascompetition/")) {
					if (event.getSubcommandName().equals("start")) {
						guildConfigs.setValue(event.getGuild(), ConfigValues.COMPETITION_RUNNING, "true");

						Util.sendSelfDestructingMessage(event.getMessageChannel(),
								"Starting the TAS Competition-Bot. `/participate` will be enabled and listening to DM's from participants", 20);
						
					} else if (event.getSubcommandName().equals("stop")) {
						guildConfigs.setValue(event.getGuild(), ConfigValues.COMPETITION_RUNNING, "false");
						
						Util.sendSelfDestructingMessage(event.getMessageChannel(),
								"Stopping the TAS Competition-Bot. Disabeling `/participate` and stop listening to DM's from participants", 20);
					}
				}
				
				// ================== Set Channel Command
				else if (commandPath.startsWith("setchannel/")) {
					if (commandPath.startsWith("setchannel/add")) {
						addChannelToConfig(event.getGuild(), event.getMessageChannel(), event.getSubcommandName(), event.getCommandIdLong());
						Util.sendSelfDestructingMessage(event.getMessageChannel(),
								"Added " + event.getSubcommandName() + " property to this channel!", 10);

					} else if (commandPath.startsWith("setchannel/remove")) {
						removeFromConfig(event.getGuild(), event.getSubcommandName());
						Util.sendSelfDestructingMessage(event.getMessageChannel(),
								"Removed " + event.getSubcommandName() + " property from any channel that has it!", 10);
					} else if (commandPath.equals("setchannel/list")) {

						String participateChannel = guildConfigs.getValue(event.getGuild(), ConfigValues.PARTICIPATECHANNEL);
						String organizerChannel = guildConfigs.getValue(event.getGuild(), ConfigValues.ORGANIZERCHANNEL);
						String submitChannel = guildConfigs.getValue(event.getGuild(), ConfigValues.SUBMITCHANNEL);

						EmbedBuilder embed = new EmbedBuilder().setTitle("Current channel settings:").setColor(color)
								.addField("Participate Channel:", participateChannel == null ? "Unset" : "<#" + participateChannel + ">", false)
								.addField("Organizer Channel:",	organizerChannel == null ? "Unset" : "<#" + organizerChannel + ">", false)
								.addField("Submission Channel:", submitChannel == null ? "Unset" : "<#" + submitChannel + ">", false);

						Util.sendDeletableMessage(event.getChannel(), new MessageBuilder(embed).build());
					}
				}
				
				// ================== Set Role Command
				else if (commandPath.startsWith("setrole/")) {
					if (commandPath.startsWith("setrole/add")) {
						addRoleToConfig(event.getGuild(), event.getOption("role").getAsRole(), event.getSubcommandName());
						Util.sendSelfDestructingMessage(event.getMessageChannel(),
								"Added "+event.getSubcommandName()+" to the config!", 10);

					} else if (commandPath.startsWith("setrole/remove")) {
						removeFromConfig(event.getGuild(), event.getSubcommandName());
						Util.sendSelfDestructingMessage(event.getMessageChannel(),
								"Removed " + event.getSubcommandName() + " from the config!", 10);
					} else if (commandPath.equals("setrole/list")) {

						String participateRole = guildConfigs.getValue(event.getGuild(), ConfigValues.PARTICIPATEROLE);
						String organizerRole = guildConfigs.getValue(event.getGuild(), ConfigValues.ORGANIZERROLE);

						EmbedBuilder embed = new EmbedBuilder().setTitle("Current role settings:").setColor(color)
								.addField("Participant Role:", participateRole == null ? "Unset" : "<@&" + participateRole + ">", false)
								.addField("Organizer Role:",	organizerRole == null ? "Unset" : "<@&" + organizerRole + ">", false);

						Util.sendDeletableMessage(event.getChannel(), new MessageBuilder(embed).build());
					}
				}
				// ================== Preview Command
				else if (commandPath.equals("preview")) {
					event.getMessageChannel().retrieveMessageById(event.getOption("messageid").getAsString()).submit().whenComplete((msg, throwable)->{
						try {
							EmbedBuilder embed=MD2Embed.parseEmbed(msg.getContentRaw(), color);
							MessageBuilder newmsg=new MessageBuilder(embed);
							Util.sendDeletableMessage(event.getChannel(), newmsg.build());
						} catch (Exception e) {
							Util.sendErrorMessage(event.getChannel(), e);
							e.printStackTrace();
						}
					});
				}
				// ================== SetRule Command
				else if (commandPath.equals("setrulemessage")) {
					event.getMessageChannel().retrieveMessageById(event.getOption("messageid").getAsString()).submit().whenComplete((msg, throwable)->{
						try {
							guildConfigs.setValue(event.getGuild(), ConfigValues.RULEMSG, msg.getContentRaw());
							MessageBuilder builder = new MessageBuilder();
							builder.setContent("Set the rule message to:");
							builder.setEmbeds(MD2Embed.parseEmbed(msg.getContentRaw(), color).build());
							Util.sendSelfDestructingMessage(event.getMessageChannel(), builder.build(), 20);
						} catch (Exception e) {
							Util.sendErrorMessage(event.getChannel(), e);
							e.printStackTrace();
						}
					});
				}
				// ================== Participate Command
				else if (commandPath.equals("participate")) {
					if(shouldExecuteParticipate(event.getGuild())) {
						if(offer!=null) {
							if(!RoleWrapper.doesMemberHaveRole(event.getMember(), guildConfigs.getValue(event.getGuild(), ConfigValues.PARTICIPATEROLE))) {
								offer.startOffer(event.getGuild(), event.getUser());
							}else {
								Message msg = new MessageBuilder("<@"+event.getUser().getId()+"> You are already participating!").build();
								Util.sendSelfDestructingMessage(event.getChannel(), msg, 10);
							}
						} else {
							Util.sendErrorMessage(event.getChannel(), new NullPointerException("This state shouldn't be possible, meaning the bot author made a bug somewhere..."));
						}
					} else {
						Util.sendSelfDestructingMessage(event.getMessageChannel(), "You can not participate currently!", 10);
					}
				}
				// ================== Submit Command
				else if (commandPath.startsWith("submit")) {
					if (commandPath.equals("submit/add")) {
						submissionHandler.submit(event.getGuild(), event.getOption("user").getAsUser(), event.getOption("submission").getAsString());
					}
					else if (commandPath.equals("submit/clear")) {
						submissionHandler.clearSubmission(event.getGuild(), event.getOption("user").getAsUser(), event.getMessageChannel());
					}
					else if (commandPath.equals("submit/clearall")) {
						submissionHandler.clearAllSubmissions(event.getGuild(), event.getMessageChannel());
					}
					else if (commandPath.equals("submit/showall")) {
						submissionHandler.sendSubmissionList(event.getGuild(), event.getMessageChannel());
					}
				}
				
			// Error handling
			} catch (Exception e) {
				Util.sendErrorMessage(event.getMessageChannel(), e);
				e.printStackTrace();
			}
			// Delete "Thinking" Message
			hook.deleteOriginal().queue();
		});
	}
	
	private void addChannelToConfig(Guild guild, MessageChannel messageChannel, String subcommandName, long l) throws Exception{
		guildConfigs.setValue(guild, subcommandName, messageChannel.getId());
	}
	
	private void removeFromConfig(Guild guild, String subcommandName) throws Exception {
		guildConfigs.removeValue(guild, subcommandName);
	}

	private void addRoleToConfig(Guild guild, Role role, String subcommandName) throws Exception {
		guildConfigs.setValue(guild, subcommandName, role.getId());
	}
	
	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event) {
		if (!Util.isThisUserThisBot(event.getUserIdLong())) {

			Emoji reactionEmote = event.getEmoji();

			if (reactionEmote.getFormatted().equals(EmojiManager.getForAlias(":x:").getUnicode())) {

				event.retrieveMessage().queue(msg -> {
					if (Util.isThisUserThisBot(msg.getAuthor())) {

						if (Util.hasBotReactedWith(msg, EmojiManager.getForAlias(":x:").getUnicode()) || event.getChannelType() == ChannelType.PRIVATE) {
							Util.deleteMessage(msg);
						}
					}
				});
			}
			
			// DMBridge Send
			else if(event.getChannelType()==ChannelType.PRIVATE) {
				event.retrieveMessage().queue(msg -> {
					if(Util.hasBotReactedWith(msg, reactionEmote.getFormatted())) {
						
						// Get participation guilds
						List<Guild> participationGuilds = UtilTASCompBot.getActiveParticipationGuilds(msg.getAuthor());
						
						// React with a checkmark when participation guild, react with more if multiple
						Guild participationGuild = null;
						
						if (participationGuilds.size() == 1) {
							
							participationGuild=participationGuilds.get(0);
							
						} else if (participationGuilds.size() > 1 && participationGuilds.size() < 10) {
							int channelNumber = 0;
							if(EmojiManager.containsEmoji(reactionEmote.getFormatted())) {
								
								String emoji=reactionEmote.getFormatted();
								
								channelNumber = UtilTASCompBot.unicodeToInt(emoji);
							}
							participationGuild=participationGuilds.get(channelNumber-1);
						}
						
						//Submit command
						String submit = MD2Embed.matchAndGet("^!submit (.+)", msg.getContentRaw(), 1);
						
						if(submit!=null) {
							
							if(isCompetitionRunning(participationGuild)) {
								User user = msg.getAuthor();
								submissionHandler.submit(participationGuild, user, msg, submit);
							}
							
						} else {
							// If no command was in the message
							if(!guildConfigs.hasValue(participationGuild, ConfigValues.ORGANIZERCHANNEL)) {
								Util.sendDeletableDirectMessage(event.getUser(), "The destination channel for the "+participationGuild.getName()+" was not set by their admins. You may alert them of this mistake...");
								return;
							}
							MessageChannel channel = (MessageChannel) participationGuild
									.getGuildChannelById(guildConfigs.getValue(participationGuild, ConfigValues.ORGANIZERCHANNEL));
							
							
							Util.sendMessage(channel, Util.constructMessageWithAuthor(msg));
							
						}
						
						msg.removeReaction(reactionEmote).queue();
					}
				});
			}
		}
	}
	
	public static TASCompBot getBot() {
		return instance;
	}

	public JDA getJDA() {
		return jda;
	}
	
	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if(event.getChannelType()==ChannelType.PRIVATE) {
			event.getChannel().retrieveMessageById(event.getMessageId()).queue(message -> {
				if (!Util.isThisUserThisBot(event.getAuthor())) {
					String msg = message.getContentRaw();
					
					// Accept
					String accept = MD2Embed.matchAndGet("^!accept (\\w{5})", msg, 1);
					
					if (accept != null) {
						User user = message.getAuthor();
						Guild guild = offer.checkCode(user, accept);
						if (guild != null) {
							Util.sendSelfDestructingDirectMessage(user, "You are now participating!", 20);
							sendPrivateCommandHelp(user);
							
							
							Role participationRole = guild
									.getRoleById(guildConfigs.getValue(guild, ConfigValues.PARTICIPATEROLE));
	
							guild.addRoleToMember(user, participationRole).queue();
	
							MessageChannel channel = (MessageChannel) guild
									.getGuildChannelById(guildConfigs.getValue(guild, ConfigValues.PARTICIPATECHANNEL));
	
							Message guildMessage = Util.constructEmbedMessage(user.getName() + " is now participating!",
									"Type `/participate` if you also want to join the TAS Competition!", color);
	
							Util.sendMessage(channel, guildMessage);
						}
						
					} else if (Pattern.matches("^!debug", msg)) {
						
						List<String> guildList= new ArrayList<>();
						UtilTASCompBot.getActiveParticipationGuilds(message.getAuthor()).forEach(guild -> {
							guildList.add(guild.getName());
						});
						Util.sendDeletableDirectMessage(message.getAuthor(), guildList.toString());
					
					} else {
						
						List<Guild> participationGuilds = UtilTASCompBot.getActiveParticipationGuilds(message.getAuthor());
						
						String checkmark = EmojiManager.getForAlias("white_check_mark").getUnicode();
						if (participationGuilds.size() == 1) {
							
							message.addReaction(Emoji.fromUnicode(checkmark)).queue();
							
						} else if (participationGuilds.size() > 1 && participationGuilds.size() < 10) {
							for (int i = 1; i <= participationGuilds.size(); i++) {

								String emote = UtilTASCompBot.intToUnicode(i);
								
								message.addReaction(Emoji.fromUnicode(emote)).queue();
							}
						}
					}
				}
			});
		}
		else if (event.getChannel().getIdLong() == Long.parseLong(guildConfigs.getValue(event.getGuild(), ConfigValues.ORGANIZERCHANNEL))) {
			
			event.getChannel().retrieveMessageById(event.getMessageId()).queue(message -> {
				
				if(message.getType()==MessageType.INLINE_REPLY) {
					MessageReference replymessage=message.getMessageReference();
					
					if(replymessage.getMessage().getEmbeds().size()!=0) {
						
						String name=replymessage.getMessage().getEmbeds().get(0).getAuthor().getName();
						User replyUser=event.getGuild().getMemberByTag(name).getUser();
						MessageBuilder newMessage=new MessageBuilder(message);
						message.getAttachments().forEach(attachment -> {
							newMessage.append(attachment.getUrl());
						});
						Util.sendDeletableDirectMessage(replyUser, newMessage.build());
					}
				}
			});
		}
	}
	

	private void sendPrivateCommandHelp(User user) {
		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle("Help/Commands");
		builder.setDescription("When DMing this bot, your message will get forwarded to the organizers. They can also answer you through the bot.\n"
				+ "If a message was forwarded correctly, the bot will react with a checkmark.\n"
				+ "There are also commands you can use in DM's:");
		builder.addField("!submit <link to submission and/or meme run>", "Adds a submission. To overwrite the last submission, just use `!submit` again.\n\n"
				+ "*Example:* `/submit Submission: https://www.youtube.com/watch?v=3Tk6WaigTQk MemeRun: https://www.youtube.com/watch?v=dQw4w9WgXcQ`",false);
		builder.addField("!help", "Get this help again", false);
		builder.setColor(color);
		Util.sendDeletableDirectMessage(user, new MessageBuilder(builder).build());
	}

	public GuildConfigs getGuildConfigs() {
		return guildConfigs;
	}
	
	public boolean isCompetitionRunning(Guild guild) {
		return guildConfigs.getValue(guild, ConfigValues.COMPETITION_RUNNING).equals("true");
	}
}
