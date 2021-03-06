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

package pl.kamil0024.moderation.commands;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.kamil0024.core.Ustawienia;
import pl.kamil0024.core.command.Command;
import pl.kamil0024.core.command.CommandContext;
import pl.kamil0024.core.command.enums.PermLevel;
import pl.kamil0024.core.util.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class StatusCommand extends Command {

    private final static Logger logger = LoggerFactory.getLogger(StatusCommand.class);
    private final static SimpleDateFormat DF = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private final EventWaiter eventWaiter;

    public StatusCommand(EventWaiter eventWaiter) {
        name = "status";
        permLevel = PermLevel.ADMINISTRATOR;
        this.eventWaiter = eventWaiter;
    }

    @Override
    public boolean execute(@NotNull CommandContext context) {
        if (context.getArgs().get(0) == null) throw new UsageException();
        TextChannel txt = context.getJDA().getTextChannelById(Ustawienia.instance.channel.status);
        if (txt == null) throw new NullPointerException("Kanal do statusu jest nullem");

        if (!txt.canTalk()) {
            context.sendTranslate("status.perms",  txt.getAsMention()).queue();
            return false;
        }

        Boolean feerko = null, roizy = null, derp = null;
        String[] serwery = context.getArgs().get(0).split(",");
        for (String s : serwery) {
            String tak = s.toLowerCase();
            if (tak.equals("feerko")) feerko = true;
            if (tak.equals("roizy")) roizy = true;
            if (tak.equals("derpmc")) derp = true;
        }

        if (feerko == null && roizy == null && derp == null) {
            context.sendTranslate("status.badservers").queue();
            return false;
        }

        Message botMsg = null;
        MessageHistory history = txt.getHistoryFromBeginning(15).complete();
        if (history.isEmpty()) {
            botMsg = txt.sendMessage(getMsg(Emote.ONLINE, Emote.ONLINE, Emote.ONLINE, null)).complete();
        }

        if (botMsg == null) {
            for (Message message : history.getRetrievedHistory()) {
                if (message.getAuthor().getId().equals(Ustawienia.instance.bot.botId)) {
                    botMsg = message;
                    break;
                }
            }
        }

        if (botMsg == null) throw new NullPointerException("Nie udało się znaleźć wiadomości bota");

        StringBuilder sb = new StringBuilder();
        if (derp != null) sb.append("derpmc, ");
        if (feerko != null) sb.append("feerko, ");
        if (roizy != null) sb.append("roizy, ");

        final Message waitMsg = context.sendTranslate("status.stats", sb.toString()).complete();
        for (Emote value : Emote.values()) {
            waitMsg.addReaction(value.getUnicode()).queue();
        }

        Boolean finalDerp = derp;
        Boolean finalRoizy = roizy;
        Boolean finalFeerko = feerko;
        Message finalBotMsg = botMsg;
        eventWaiter.waitForEvent(GuildMessageReactionAddEvent.class,
                (event) -> event.getUser().getId().equals(context.getUser().getId()) && event.getMessageId().equals(waitMsg.getId()),
                (event) -> {
                    waitMsg.clearReactions().queue();
                    if (event.getReactionEmote().isEmote()) return;

                    Emote e = Emote.byUnicode(event.getReaction().getReactionEmote().getEmoji());
                    if (e == null) return;

                    finalBotMsg.editMessage(getMsg(finalDerp == null ? null : e,
                            finalFeerko == null ? null : e,
                            finalRoizy == null ? null : e, finalBotMsg.getContentRaw())).queue();

                    waitMsg.editMessage(context.getTranslate("status.succes")).queue();
                    waitMsg.clearReactions().queue();
                },30, TimeUnit.SECONDS, () -> waitMsg.clearReactions().queue());

        return true;
    }

    @Getter
    public enum Emote {
        ONLINE("\u2705", "Serwer chodzi sprawnie, bądź nie wiemy o problemie"),
        WARN("\u26A0", "Występują problemy na serwerze, sprawdzamy przyczynę"),
        OFF("\uD83D\uDED1", "Awaria serwera"),
        PRZERWA("\uD83D\uDEE0️", "Serwer ma przerwę techniczną"),
        RESTART("\uD83D\uDD04", "Serwer jest restartowany");

        public String unicode;
        public String opis;

        Emote(String unicode, String opis) {
            this.unicode = unicode;
            this.opis = opis;
        }

        @Nullable
        public static Emote byUnicode(String uni) {
            for (Emote value : Emote.values()) {
                if (value.getUnicode().equals(uni)) return value;
            }
            return null;
        }

    }

    @Getter
    public enum MojangEmote {
        NULL("❓", "null", "null"),
        GREEN("\uD83D\uDFE9", "green", "Serwis nie ma żadnych problemów"),
        YELLOW("\uD83D\uDFE8", "yellow", "Serwis ma problemy"),
        RED("\uD83D\uDFE5", "red", "Serwis jest wyłączony");

        public String unicode;
        public String opis;
        public String tlumaczenie;

        MojangEmote(String unicode, String opis, String tlumaczenie) {
            this.unicode = unicode;
            this.opis = opis;
            this.tlumaczenie = tlumaczenie;
        }

        public static MojangEmote byOpis(String opis) {
            for (MojangEmote value : MojangEmote.values()) {
                if (value.getOpis().equalsIgnoreCase(opis)) return value;
            }
            return MojangEmote.NULL;
        }
    }

    @SuppressWarnings("ConstantConditions")
    public static String getMsg(@Nullable Emote derp, @Nullable Emote feerko, @Nullable Emote roizy, @Nullable String botMsg) {
        String xd = "\uD83D\uDD36";
        BetterStringBuilder sb = new BetterStringBuilder();

        if (botMsg == null) {
            if (derp == null) derp = Emote.ONLINE;
            if (feerko == null) feerko = Emote.ONLINE;
            if (roizy == null) roizy = Emote.ONLINE;
        } else {
            for (String s : botMsg.split("\n")) {
                if (!s.isEmpty() && s.contains("-> ")) {
                    String emote = s.split("-> ")[1];
                    if (derp == null) if (s.contains("DerpMC.PL")) derp = Emote.byUnicode(emote);
                    if (feerko == null) if (s.contains("Feerko.PL")) feerko = Emote.byUnicode(emote);
                    if (roizy == null) if (s.contains("RoiZy.PL")) roizy = Emote.byUnicode(emote);
                }
            }
        }

        sb.appendLine(xd + " **STATUS SERWERÓW** " + xd);
        sb.appendLine("Na tym kanale możecie sprawdzić status serwerów. Status jest aktualizowany ręcznie przez nas, więc tutaj możecie się dowiedzieć, czy jesteśmy świadomi sytuacji i jakie kroki już podejmujemy.\n");

        sb.appendLine("DerpMC.PL -> " + derp.getUnicode());
        sb.appendLine("Feerko.PL -> " + feerko.getUnicode());
        sb.appendLine("RoiZy.PL -> " + roizy.getUnicode() + "\n");

        sb.appendLine(xd + " **STATUS MOJANGU** " + xd);
        sb.appendLine("Automatycznie co pięć minut jest aktualizowany status serwerów Mojanga. Jeżeli któryś status będzie wynosi " + MojangEmote.RED.getUnicode() + " lub " + MojangEmote.YELLOW.getUnicode() + " dołączenie na nasze serwery może być utrudnione.\n");

        try {
            JSONArray json = NetworkUtil.getJsonArray("https://status.mojang.com/check");
            if (json == null || json.get(0) == null) throw new NullPointerException("json == null");
            for (Object o : json) {
                JSONObject obj = (JSONObject) o;
                for (Object name : obj.names()) {
                    if (name.equals("session.minecraft.net") || name.equals("authserver.mojang.com") || name.equals("api.mojang.com") || name.equals("account.mojang.com")) {
                        try {
                            sb.appendLine(name + " -> " + MojangEmote.byOpis(obj.getString((String) name)).getUnicode());
                        } catch (Exception e) {
                            e.printStackTrace();
                            sb.appendLine(name + " -> " + MojangEmote.NULL.getUnicode());
                        }
                    }
                }
            }

        } catch (Exception e) {
            StringWriter er = new StringWriter();
            e.printStackTrace(new PrintWriter(er));
            logger.error(er.toString());
            sb.appendLine("❗ Nie udało się uzyskać statusów od Mojangu ❗");
        }

        sb.appendLine("\n**Data ostatniej aktualizacji**: " + DF.format(new Date()) + "\n");

        sb.appendLine("Legenda:");

        for (Emote value : Emote.values()) {
            sb.appendLine(value.getUnicode() + " " + value.getOpis());
        }
        sb.appendLine("\n");
        for (MojangEmote value : MojangEmote.values()) {
            if (value == MojangEmote.NULL) continue;
            sb.appendLine(value.getUnicode() + " " + value.getTlumaczenie());
        }

        return sb.toString();
    }

}
