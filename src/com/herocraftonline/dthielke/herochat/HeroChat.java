/**
 * Copyright (C) 2011 DThielke <dave.thielke@gmail.com>
 * 
 * This work is licensed under the Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/3.0/ or send a letter to
 * Creative Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.
 **/

package com.herocraftonline.dthielke.herochat;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.ensifera.animosity.craftirc.CraftIRC;
import com.herocraftonline.dthielke.herochat.channels.ChannelManager;
import com.herocraftonline.dthielke.herochat.command.CommandManager;
import com.herocraftonline.dthielke.herochat.command.commands.BanCommand;
import com.herocraftonline.dthielke.herochat.command.commands.CreateCommand;
import com.herocraftonline.dthielke.herochat.command.commands.FocusCommand;
import com.herocraftonline.dthielke.herochat.command.commands.HelpCommand;
import com.herocraftonline.dthielke.herochat.command.commands.IgnoreCommand;
import com.herocraftonline.dthielke.herochat.command.commands.JoinCommand;
import com.herocraftonline.dthielke.herochat.command.commands.KickCommand;
import com.herocraftonline.dthielke.herochat.command.commands.LeaveCommand;
import com.herocraftonline.dthielke.herochat.command.commands.ListCommand;
import com.herocraftonline.dthielke.herochat.command.commands.ModCommand;
import com.herocraftonline.dthielke.herochat.command.commands.QuickMsgCommand;
import com.herocraftonline.dthielke.herochat.command.commands.ReloadCommand;
import com.herocraftonline.dthielke.herochat.command.commands.RemoveCommand;
import com.herocraftonline.dthielke.herochat.command.commands.WhoCommand;
import com.herocraftonline.dthielke.herochat.util.ConfigManager;
import com.herocraftonline.dthielke.herochat.util.PermissionHelper;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

public class HeroChat extends JavaPlugin {

    public enum ChatColor {
        BLACK("�0"),
        NAVY("�1"),
        GREEN("�2"),
        BLUE("�3"),
        RED("�4"),
        PURPLE("�5"),
        GOLD("�6"),
        LIGHT_GRAY("�7"),
        GRAY("�8"),
        DARK_PURPLE("�9"),
        LIGHT_GREEN("�a"),
        LIGHT_BLUE("�b"),
        ROSE("�c"),
        LIGHT_PURPLE("�d"),
        YELLOW("�e"),
        WHITE("�f");

        public final String str;

        ChatColor(String str) {
            this.str = str;
        }
    }

    private Logger log = Logger.getLogger("Minecraft");
    private ChannelManager channelManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private PermissionHelper permissions;
    private CraftIRC craftIRC;
    private String ircMessageFormat;
    private String ircTag;
    private String tag;
    private List<String> censors;
    private HeroChatPlayerListener playerListener;
    private HeroChatCraftIRCListener craftIRCListener;
    private boolean eventsRegistered = false;

    @Override
    public void onDisable() {
        try {
            for (Player p : getServer().getOnlinePlayers()) {
                configManager.savePlayer(p.getName());
                configManager.save();
            }
        } catch (Exception e) {}
        PluginDescriptionFile desc = getDescription();
        log(Level.INFO, desc.getName() + " version " + desc.getVersion() + " disabled.");
    }

    @Override
    public void onEnable() {
        channelManager = new ChannelManager(this);
        permissions = loadPermissions();
        if (permissions == null) {
            return;
        }
        loadCraftIRC();
        registerEvents();
        registerCommands();
        PluginDescriptionFile desc = getDescription();
        log(Level.INFO, desc.getName() + " version " + desc.getVersion() + " enabled.");

        configManager = new ConfigManager(this);
        try {
            configManager.load();
        } catch (Exception e) {
            log(Level.WARNING, "Error encountered while loading data. Check your config.yml and users.yml. Disabling HeroChat.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        for (Player p : getServer().getOnlinePlayers()) {
            playerListener.onPlayerJoin(new PlayerEvent(Event.Type.PLAYER_JOIN, p));
        }

        try {
            configManager.save();
        } catch (Exception e) {
            log(Level.WARNING, "Error encountered while saving data. Disabling HeroChat.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return commandManager.dispatch(sender, command, label, args);
    }

    private void registerEvents() {
        if (!eventsRegistered) {
            playerListener = new HeroChatPlayerListener(this);
            PluginManager pm = getServer().getPluginManager();
            pm.registerEvent(Event.Type.PLAYER_CHAT, playerListener, Event.Priority.Low, this);
            pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Event.Priority.Normal, this);
            pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Event.Priority.Normal, this);
            pm.registerEvent(Event.Type.PLAYER_COMMAND_PREPROCESS, playerListener, Event.Priority.Normal, this);
            eventsRegistered = true;
        }
    }

    private void registerCommands() {
        commandManager = new CommandManager();
        commandManager.addCommand(new HelpCommand(this));
        commandManager.addCommand(new BanCommand(this));
        commandManager.addCommand(new ListCommand(this));
        commandManager.addCommand(new CreateCommand(this));
        commandManager.addCommand(new FocusCommand(this));
        commandManager.addCommand(new IgnoreCommand(this));
        commandManager.addCommand(new JoinCommand(this));
        commandManager.addCommand(new KickCommand(this));
        commandManager.addCommand(new LeaveCommand(this));
        commandManager.addCommand(new WhoCommand(this));
        commandManager.addCommand(new ModCommand(this));
        commandManager.addCommand(new QuickMsgCommand(this));
        commandManager.addCommand(new ReloadCommand(this));
        commandManager.addCommand(new RemoveCommand(this));
    }

    private PermissionHelper loadPermissions() {
        Plugin p = this.getServer().getPluginManager().getPlugin("Permissions");
        if (p != null) {
            Permissions permissions = (Permissions) p;
            if (!permissions.isEnabled()) {
                this.getServer().getPluginManager().enablePlugin(permissions);
            }
            boolean upToDate = true;
            String version = permissions.getDescription().getVersion();
            String[] split = version.split("\\.");
            try {
                for (int i = 0; i < split.length; i++) {
                    int v = Integer.parseInt(split[i]);
                    if (v < PermissionHelper.MIN_VERSION[i]) {
                        upToDate = false;
                    }
                }
            } catch (NumberFormatException e) {
                upToDate = false;
            }
            if (upToDate) {
                PermissionHandler security = permissions.getHandler();
                PermissionHelper ph = new PermissionHelper(security);
                log(Level.INFO, "Permissions " + version + " found.");
                return ph;
            }
        }

        log.log(Level.WARNING, "Permissions 2.5.1 or higher not found! Please update Permissions. Disabling HeroChat.");
        this.getPluginLoader().disablePlugin(this);
        return null;
    }

    private void loadCraftIRC() {
        Plugin p = this.getServer().getPluginManager().getPlugin("CraftIRC");
        if (p != null) {
            try {
                craftIRC = (CraftIRC) p;
                craftIRCListener = new HeroChatCraftIRCListener(this);
                this.getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT, craftIRCListener, Event.Priority.Normal, this);
                log(Level.INFO, "CraftIRC found.");
            } catch (ClassCastException ex) {
                ex.printStackTrace();
                log(Level.WARNING, "Error encountered while connecting to CraftIRC!");
                craftIRC = null;
                craftIRCListener = null;
            }
        }
    }

    public void log(Level level, String msg) {
        log.log(level, "[HeroChat] " + msg);
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public PermissionHelper getPermissions() {
        return permissions;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CraftIRC getCraftIRC() {
        return craftIRC == null ? null : craftIRC;
    }

    public void setIrcTag(String ircTag) {
        this.ircTag = ircTag;
    }

    public String getIrcTag() {
        return ircTag;
    }

    public void setIrcMessageFormat(String ircMessageFormat) {
        this.ircMessageFormat = ircMessageFormat;
    }

    public String getIrcMessageFormat() {
        return ircMessageFormat;
    }

    public void setCensors(List<String> censors) {
        this.censors = censors;
    }

    public List<String> getCensors() {
        return censors;
    }
}
