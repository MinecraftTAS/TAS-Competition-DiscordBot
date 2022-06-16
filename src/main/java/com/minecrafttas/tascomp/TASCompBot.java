package com.minecrafttas.tascomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;

import javax.security.auth.login.LoginException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.minecrafttas.tascomp.GuildConfigs.ConfigValues;
import com.minecrafttas.tascomp.util.EmoteWrapper;
import com.minecrafttas.tascomp.util.Util;
import com.vdurmont.emoji.EmojiManager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;
import net.dv8tion.jda.internal.requests.restaction.MessageActionImpl;

public class TASCompBot extends ListenerAdapter implements Runnable {

	private static TASCompBot instance;
	private final JDA jda;
	private final Properties configuration;
	private final GuildConfigs guildConfigs;
	private static final Logger LOGGER = LoggerFactory.getLogger("TAS Competition");
	private final int color=0x0a8505;
	
	public TASCompBot(Properties configuration) throws InterruptedException, LoginException {
		instance=this;
		this.configuration = configuration;
		final JDABuilder builder = JDABuilder.createDefault(this.configuration.getProperty("token"))
				.setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(this);
		this.guildConfigs=new GuildConfigs(LOGGER);
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
		prepareCommands(guild);
		guildConfigs.prepareConfig(guild);
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
		setChannelCommand.setDefaultEnabled(false);
		
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
		tascompCommand.setDefaultEnabled(false);
		
		SubcommandData[] tascompSubcommands= {
				new SubcommandData("start", "Starts a TAS Competition in this guild"),
				new SubcommandData("stop", "Stops a running TAS Competition in this guild")
		};
		tascompCommand.addSubcommands(tascompSubcommands);
		
		// =========================Set Role
		CommandDataImpl setRoleCommand = new CommandDataImpl("setrole", "Set's roles for this guild");
		setRoleCommand.setDefaultEnabled(false);
		
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
		previewCommand.setDefaultEnabled(false);
		
		OptionData messageIDOption = new OptionData(OptionType.STRING, "messageid", "The message id to preview");
		messageIDOption.setRequired(true);
		previewCommand.addOptions(messageIDOption);
		
		updater.addCommands(tascompCommand, setChannelCommand, setRoleCommand, previewCommand);
		updater.queue();
		LOGGER.info("Done preparing commands!");
	}
	
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		LOGGER.info("{}: Running slash command {}",event.getUser().getAsTag(), event.getCommandPath());
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
				if (commandPath.startsWith("setchannel/")) {
					if (commandPath.startsWith("setchannel/add")) {

						addChannelToConfig(event.getGuild(), event.getMessageChannel(), event.getSubcommandName());
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
				if (commandPath.startsWith("setrole/")) {
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
				if (commandPath.equals("preview")) {
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
			// Error handling
			} catch (Exception e) {
				Util.sendErrorMessage(event.getMessageChannel(), e);
				e.printStackTrace();
			}
			// Delete "Thinking" Message
			hook.deleteOriginal().queue();
		});
	}
	
	private void addChannelToConfig(Guild guild, MessageChannel messageChannel, String subcommandName) throws Exception{
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
		if (!Util.isThisUserThisBot(event.getUser())) {

			ReactionEmote reactionEmote = event.getReactionEmote();

			if (EmoteWrapper.getReactionEmoteId(reactionEmote).equals(EmojiManager.getForAlias(":x:").getUnicode())) {

				event.retrieveMessage().queue(msg -> {
					if (Util.isThisUserThisBot(msg.getAuthor())) {

						if (Util.hasBotReactedWith(msg, EmojiManager.getForAlias(":x:").getUnicode())) {
							Util.deleteMessage(msg);
						}
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
//		System.out.println(event.getMessage().getContentRaw());
	}
}
