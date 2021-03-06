/*
 *
 *    Copyright 2020 P2WB0T
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package pl.kamil0024.core.command;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.kamil0024.commands.dews.RebootCommand;
import pl.kamil0024.core.Ustawienia;
import pl.kamil0024.core.arguments.ArgumentManager;
import pl.kamil0024.core.command.enums.CommandCategory;
import pl.kamil0024.core.command.enums.PermLevel;
import pl.kamil0024.core.database.UserDao;
import pl.kamil0024.core.database.config.UserConfig;
import pl.kamil0024.core.logger.Log;
import pl.kamil0024.core.util.Error;
import pl.kamil0024.core.util.*;

import java.time.Instant;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandExecute extends ListenerAdapter {

    private final Logger logger = LoggerFactory.getLogger(CommandExecute.class);

    ArgumentManager argumentManager;
    CommandManager commandManager;
    Tlumaczenia tlumaczenia;
    UserDao userDao;

    @Getter HashMap<String, UserConfig> userConfig;

    private final Map<String, Instant> cooldowns = new HashMap<>();

    public CommandExecute(CommandManager commandManager, Tlumaczenia tlumaczenia, ArgumentManager argumentManager, UserDao userDao) {
        this.commandManager = commandManager;
        this.tlumaczenia = tlumaczenia;
        this.argumentManager = argumentManager;
        this.userDao = userDao;
        reloadConfig();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (!e.isFromGuild() || e.getAuthor().isBot() || e.getMessage().isWebhookMessage() ||
                e.getMessage().getContentRaw().isEmpty()) {
            return;
        }
        boolean inRekru = e.getGuild().getId().equals(Ustawienia.instance.rekrutacyjny.guildId);

        if (!inRekru && !e.getGuild().getId().equals(Ustawienia.instance.bot.guildId)) return;

        String prefix = Ustawienia.instance.prefix;

        UserConfig uc = userConfig.get(e.getAuthor().getId());
        if (uc != null) prefix = uc.getPrefix();

        String msg = e.getMessage().getContentRaw();
        String[] args = msg.split(" ");

        if (args.length == 0 || !args[0].startsWith(prefix)) return;

        String cmd = args[0].replaceAll(prefix, "").toLowerCase();
        if (cmd.isEmpty()) return;

        Command c = commandManager.commands.get(cmd);

        if (c == null) {
            for (Map.Entry<String, Command> alias : commandManager.getAliases().entrySet()) {
                if (alias.getKey().toLowerCase().equals(cmd)) {
                    c = alias.getValue();
                    break;
                }
            }
        }

        if (c == null) return;

        if (RebootCommand.reboot) {
            e.getChannel().sendMessage("Bot jest podczas restartowania...").queue();
            zareaguj(e.getMessage(), e.getAuthor(), false);
            return;
        }

        PermLevel jegoPerm = UserUtil.getPermLevel(e.getAuthor());

        if (inRekru && !c.isEnabledInRekru()) {
            e.getChannel().sendMessage(e.getAuthor().getAsMention() + ", ta komenda nie jest dostępna na tym serwerze!").queue();
            zareaguj(e.getMessage(), e.getAuthor(), false);
            return;
        }

        if (!inRekru && c.isOnlyInRekru()) {
            e.getChannel().sendMessage(e.getAuthor().getAsMention() + ", ta komenda jest dostępna tylko na serwerze rekrutacyjnym!").queue();
            zareaguj(e.getMessage(), e.getAuthor(), false);
            return;
        }

        if (c.getCategory() == CommandCategory.PRIVATE_CHANNEL) {
            if (!e.getChannel().getId().equals("426864003562864641") && jegoPerm.getNumer() == PermLevel.MEMBER.getNumer()) {
                e.getChannel().sendMessage(e.getAuthor().getAsMention() + ", komend muzycznych musisz używać na <#426864003562864641>!")
                        .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                return;
            }
        }

        if (Ustawienia.instance.disabledCommand.contains(e.getChannel().getId())) {
            if (jegoPerm.getNumer() == PermLevel.MEMBER.getNumer()) {
                zareaguj(e.getMessage(), e.getAuthor(), false);
                Runnable task = () -> {
                    try {
                        e.getMessage().clearReactions().complete();
                    } catch (Exception ignored) { }
                };
                ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
                ses.schedule(task, 1, TimeUnit.SECONDS);
                return;
            }
        }

        e.getChannel().sendTyping().queue();

        if (c.getPermLevel().getNumer() > jegoPerm.getNumer()) {
            String wymaga = tlumaczenia.get(c.getPermLevel().getTranlsateKey());
            String ma = tlumaczenia.get(jegoPerm.getTranlsateKey());
            String trans = "generic.noperm";
            if (c.getCategory() == CommandCategory.MUSIC) trans = "generic.ytnoperm";

            e.getChannel().sendMessage(tlumaczenia.get(trans, wymaga, c.getPermLevel().getNumer(),
                    ma, jegoPerm.getNumer())).queue();

            zareaguj(e.getMessage(), e.getAuthor(), false);
            return;
        }

        if (haveCooldown(e.getAuthor(), c) != 0) {
            e.getChannel().sendMessage(tlumaczenia.get("generic.cooldown", haveCooldown(e.getAuthor(), c))).queue();
            zareaguj(e.getMessage(), e.getAuthor(), false);
            return;
        }

        boolean udaloSie = false;
        HashMap<Integer, String> parsedArgs = new HashMap<>();
        int jest = 0;
        for (int i = 1; i < args.length; i++) {
            if (!args[i].isEmpty()) {
                parsedArgs.put(jest, args[i]);
                jest++;
            }
        }

        CommandContext cmdc = new CommandContext(e, prefix, parsedArgs, tlumaczenia, argumentManager, c);
        try {
            if (c.execute(cmdc)) udaloSie = true;
        } catch (UsageException u) {
            Error.usageError(cmdc);
        } catch (Exception omegalul) {
            omegalul.printStackTrace();
            Log.newError("`%s` uzyl komendy %s (%s) ale wystapil blad: %s", CommandExecute.class, e.getAuthor().getName(), c.getName(), c.getClass().getName(), omegalul);
            Log.newError(omegalul, CommandExecute.class);

            SentryEvent event = new SentryEvent();
            io.sentry.protocol.User user = new io.sentry.protocol.User();
            user.setId(e.getAuthor().getId());
            user.setUsername(UserUtil.getName(e.getAuthor()));
            event.setUser(user);
            event.setLevel(SentryLevel.ERROR);
            event.setLogger(getClass().getName());
            event.setThrowable(omegalul);
            Sentry.captureEvent(event);

            e.getChannel().sendMessage(String.format("Wystąpił błąd! `%s`.", omegalul)).queue();
        }
        if (udaloSie && jegoPerm.getNumer() < PermLevel.DEVELOPER.getNumer()) setCooldown(e.getAuthor(), c);
        zareaguj(e.getMessage(), e.getAuthor(), udaloSie);

        try {
            onExecuteEvent(cmdc);
        } catch (Exception ignored) {}
    }

    @NotNull
    public static Emote getReaction(User user, boolean bol) {
        try {
            Guild g = user.getJDA().getGuildById(Ustawienia.instance.bot.guildId);
            if (g == null) throw new IllegalArgumentException("guild == null");

            Emote em = g.getEmoteById(Ustawienia.instance.emote.green);
            if (!bol) em = g.getEmoteById(Ustawienia.instance.emote.red);

            if (em == null) throw new IllegalArgumentException("emote == null");
            return em;
        } catch (Exception e) {
            Log.newError(e, CommandExecute.class);
        }
        //noinspection ConstantConditions
        return null;
    }

    private static void zareaguj(Message msg, User user, boolean bol) {
        try {
            msg.addReaction(getReaction(user, bol)).complete();
        } catch (ErrorResponseException ignored) { }
    }

    private void setCooldown(User user, Command command) {
        Calendar cal = Calendar.getInstance();
        if (command.getCooldown() == 0) return;
        cal.add(Calendar.SECOND, command.getCooldown());
        cooldowns.put(user.getId() + command.getName(), cal.toInstant());
    }

    private int haveCooldown(User user, Command command) {
        if (command.getCooldown() == 0) return 0;

        Instant cooldown = cooldowns.getOrDefault(user.getId() + command.getName(), Instant.now());
        long teraz = Instant.now().toEpochMilli();

        if (cooldown.toEpochMilli() - teraz > 0) {
            return (int) TimeUnit.SECONDS.convert(cooldown.toEpochMilli() - teraz, TimeUnit.MILLISECONDS);
        }
        return 0;
    }

    public void onExecuteEvent(@Nullable CommandContext context) {
        if (context == null) return;
        String msg = "`%s` użył komendy %s(%s) na serwerze %s[%s]";
        StringBuilder b = new StringBuilder(" ");

        for (Map.Entry<Integer, String> m : context.getArgs().entrySet()) { b.append(m.getValue()).append(",");}

        msg = String.format(msg, UserUtil.getLogName(context.getUser()),
                context.getCommand(), b.toString().replaceAll("@", "@\u200b$1"), context.getGuild(), context.getGuild().getId());

        WebhookUtil web = new WebhookUtil();
        web.setMessage(msg);
        web.setType(WebhookUtil.LogType.CMD);
        web.send();
        logger.debug(msg);
    }

    public void reloadConfig() {
        this.userConfig = new HashMap<>();
        for (UserConfig userConfig : userDao.getAll()) {
            getUserConfig().put(userConfig.getId(), userConfig);
        }
    }

}
