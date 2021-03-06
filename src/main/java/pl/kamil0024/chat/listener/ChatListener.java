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

package pl.kamil0024.chat.listener;

import com.google.inject.Inject;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.Nullable;
import pl.kamil0024.chat.Action;
import pl.kamil0024.moderation.listeners.ModLog;
import pl.kamil0024.moderation.commands.MuteCommand;
import pl.kamil0024.moderation.commands.PunishCommand;
import pl.kamil0024.core.Main;
import pl.kamil0024.core.Ustawienia;
import pl.kamil0024.core.command.enums.PermLevel;
import pl.kamil0024.core.database.CaseDao;
import pl.kamil0024.core.logger.Log;
import pl.kamil0024.core.util.Emoji;
import pl.kamil0024.core.util.UserUtil;
import pl.kamil0024.core.util.kary.Dowod;
import pl.kamil0024.core.util.kary.KaryJSON;
import pl.kamil0024.logs.logger.FakeMessage;
import pl.kamil0024.stats.StatsModule;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;


import static java.nio.charset.StandardCharsets.*;

@SuppressWarnings("DuplicatedCode")
public class ChatListener extends ListenerAdapter {

    private static final Pattern HTTP = Pattern.compile("([0-9a-z_-]+\\.)+(com|infonet|net|org|pro|de|ggmc|md|me|tt|tv|uk|us|uy|uz|va|vc|ve|vg|vi|vn|vu|wf|ws|ye|yt)");
    private static final Pattern DISCORD_INVITE = Pattern.compile("(https?://)?(www\\.)?(discord\\.(gg|io|me|li)|discordapp\\.com/invite)/.+[a-z]");
    private static final Pattern EMOJI = Pattern.compile("<(a?):(\\w{2,32}):(\\d{17,19})>");
    private static final Pattern YOUTUBE_LINK = Pattern.compile("(http(s)?://)?(www\\.)?(m\\.)?(youtube\\.com|youtu\\.be)/\\S+");
    private static final Pattern SKROTY = Pattern.compile("[jJ][ ]?[a-z-A-Z]{1,2}");

    @Inject private final KaryJSON karyJSON;
    @Inject private final CaseDao caseDao;
    @Inject private final ModLog modLog;
    @Inject private final StatsModule statsModule;
    @Inject private final KaryListener karyListener;

    @Getter private final List<String> przeklenstwa;

    public ChatListener(KaryJSON karyJSON, CaseDao caseDao, ModLog modLog, StatsModule statsModule, KaryListener karyListener) {
        this.karyJSON = karyJSON;
        this.modLog = modLog;
        this.caseDao = caseDao;
        this.statsModule = statsModule;
        this.karyListener = karyListener;
        this.przeklenstwa = loadPrzeklenstwa();
    }

    public static List<String> loadPrzeklenstwa() {
        InputStream res = Main.class.getClassLoader().getResourceAsStream("przeklenstwa.api");
        if (res == null) {
            Log.newError("Plik przeklenstwa.api jest nullem", ChatListener.class);
            throw new NullPointerException("Plik przeklenstwa.api jest nullem");
        }

        List<String> przeklenstwa = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(res, UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) { przeklenstwa.add(line); }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (przeklenstwa.isEmpty()) Log.newError("Lista przeklenstw jest nullem!", ChatListener.class);
        return przeklenstwa;
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent e) {
        if (!e.getGuild().getId().equals(Ustawienia.instance.bot.guildId) || e.getAuthor().isBot()) return;
        if (e.getChannel().getParent() != null && e.getChannel().getParent().getId().equals("539819570358386698")) return;
        if (UserUtil.getPermLevel(e.getAuthor()).getNumer() >= PermLevel.CHATMOD.getNumer()) return;
        if (e.getAuthor().isBot() || e.getMessage().getContentRaw().isEmpty()) return;
        if (e.getChannel().getId().equals("426809411378479105") || e.getChannel().getId().equals("503294063064121374") || e.getChannel().getId().equals("573873102757429256")) return;

        char kurwa = 'a';
        try {
            kurwa = e.getMessage().getContentRaw().toCharArray()[1];
        } catch (Exception ignored) { }

        if (e.getChannel().getId().equals("426864003562864641") && !e.getAuthor().isBot() &&
                !e.getMessage().getContentRaw().isEmpty() && kurwa == 'p') {
            return;
        }
        checkMessage(e.getMember(), e.getMessage(), karyJSON, caseDao, modLog);
    }

    @Override
    public void onGuildMessageUpdate(@Nonnull GuildMessageUpdateEvent e) {
        if (!e.getGuild().getId().equals(Ustawienia.instance.bot.guildId)) return;
        if (UserUtil.getPermLevel(e.getAuthor()).getNumer() >= PermLevel.CHATMOD.getNumer()) return;
        if (e.getAuthor().isBot() || e.getMessage().getContentRaw().isEmpty()) return;
        if (e.getChannel().getId().equals("426809411378479105") || e.getChannel().getId().equals("503294063064121374") || e.getChannel().getId().equals("573873102757429256")) return;
        checkMessage(e.getMember(), e.getMessage(), karyJSON, caseDao, modLog);
    }

    public void checkMessage(Member member, Message msg, KaryJSON karyJSON, CaseDao caseDao, ModLog modLog) {
        if (MuteCommand.hasMute(member)) return;

        String czystaWiadomosc = msg.getContentRaw();
        String[] split = czystaWiadomosc.split("\n");
        if (czystaWiadomosc.startsWith("> ") && split.length >= 1) {
            try {
                czystaWiadomosc = czystaWiadomosc.replaceAll(split[0], "");
            } catch (Exception ignored) { }
        }

        String msgRaw = czystaWiadomosc.replaceAll("<@!?([0-9])*>", "")
                .replaceAll("3", "e")
                .replaceAll("1", "i")
                .replaceAll("0", "o")
                .replaceAll("v", "u")
                .replaceAll("<#(\\d+)>", "");
        Action action = new Action();
        action.setMsg(FakeMessage.convert(msg));

        String przeklenstwa = msgRaw;

        String[] tak = new String[] {"a;ą", "c;ć","e;ę", "l;ł", "n;ń", "o;ó", "s;ś", "z;ź", "z;ż"};
        for (String s : tak) {
            String[] kurwa = s.split(";");
            przeklenstwa = przeklenstwa.replaceAll(kurwa[1], kurwa[0]);
        }
        przeklenstwa = przeklenstwa.replaceAll("[^\\u0020\\u0030-\\u0039\\u0041-\\u005A\\u0061-\\u007A\\u00C0-\\u1D99]", "");

        if (containsSwear(przeklenstwa.split(" ")) != null) {
            msg.delete().queue();
//                msg.getChannel().sendMessage(String.format("<@%s>, ładnie to tak przeklinać?", msg.getAuthor().getId())).queue();

            KaryJSON.Kara kara = karyJSON.getByName("Wszelkiej maści wyzwiska, obraza, wulgaryzmy, prowokacje, groźby i inne formy przemocy");
            Dowod d = new Dowod(1, msg.getGuild().getSelfMember().getId(), msg.getContentDisplay(), null);
            if (kara == null) {
                Log.newError("Powod przy nadawaniu kary za przeklenstwa jest nullem", ChatListener.class);
            } else {
                PunishCommand.putPun(kara,
                        Collections.singletonList(member),
                        member.getGuild().getSelfMember(),
                        msg.getTextChannel(),
                        caseDao, modLog, statsModule, d, null);
                return;
            }
        }

        if (containsLink(msgRaw.split(" ")) && !msg.getChannel().getId().equals("426864003562864641")) {
            action.setKara(Action.ListaKar.LINK);
            action.send(karyListener, msg.getGuild());
            return;
        }

        if (containsInvite(msgRaw.split(" "))) {
            msg.delete().queue();
            action.setKara(Action.ListaKar.LINK);
            action.send(karyListener, msg.getGuild());
            return;
        }

        if (YOUTUBE_LINK.matcher(msgRaw).find()) {
            if (!msg.getChannel().getId().equals("426864003562864641")) {
                Role miniyt = member.getGuild().getRoleById("425670776272715776");
                Role yt = member.getGuild().getRoleById("425670600049295360");
                if (miniyt == null || yt == null) {
                    Log.newError("Rola miniyt/yt jest nullem", ChatListener.class);
                    return;
                }
                if (!member.getRoles().contains(miniyt) || !member.getRoles().contains(yt)) {
                    msg.delete().queue();
                    action.setKara(Action.ListaKar.LINK);
                    action.send(karyListener, msg.getGuild());
                }
            }

        }

        String takMsg = czystaWiadomosc.replaceAll("<@(&?)(!?)([0-9])*>", "")
                .replaceAll("<#(\\d+)>", "");

        String bezEmotek = takMsg.replaceAll(EMOJI.toString(), "");
        String capsMsg = bezEmotek.replaceAll("[^\\w\\s]*", "");

        if (!msg.getChannel().getId().equals("652927860943880224")) {
            if (containsCaps(capsMsg) >= 50 || emoteCount(takMsg, msg.getJDA()) >= 10) {
                msg.delete().queue();
                action.setKara(Action.ListaKar.FLOOD);
                action.send(karyListener, msg.getGuild());
                return;
            }
            if (containsFlood(bezEmotek) >= 10) {
                action.setPewnosc(false);
                action.setDeleted(false);
                action.setKara(Action.ListaKar.FLOOD);
                action.send(karyListener, msg.getGuild());
                return;
            }
        }

        // Może to nie być w 100% prawdziwe
        action.setPewnosc(false);
        action.setDeleted(false);

        for (String s : getPrzeklenstwa()) {
            if (przeklenstwa.toLowerCase().contains(s) || przeklenstwa.replaceAll(" ", "").toLowerCase().contains(s)) {
                action.setKara(Action.ListaKar.ZACHOWANIE);
                action.send(karyListener, msg.getGuild());
                return;
            }
        }

    }

    @Nullable
    public String containsSwear(String[] list) {
        for (String s : list) {
            if (s != null && !s.isEmpty()) {
                if (getPrzeklenstwa().contains(s.toLowerCase())) return s.toLowerCase();
            }
        }
        return null;
    }

    public static boolean containsLink(String[] list) {
        for (String s : list) {
            if (s.contains("derpmc") || s.contains("roizy") || s.contains("p2w") || s.contains("hypixel") || s.contains("discord")) continue;
            try {
                new URL(s);
                return true;
            } catch (MalformedURLException e) {
                Matcher mat = HTTP.matcher(s);
                if (mat.matches()) return true;
            }
        }
        return false;
    }

    public static boolean containsInvite(String[] list) {
        for (String s : list) {
            if (DISCORD_INVITE.matcher(s).find()) return true;
        }
        return false;
    }

    public static int containsFlood(String msg) {
        if (msg.length() < 3 || containsLink(new String[] {msg})) return 0;

        int tak = 0;
        int flood = 0;
        String[] ssplit = msg.split("");
        String floodowanyZnak = null;
        for (String split : ssplit) {
            try {
                if (split.equals(" ")) continue;
                String nastepnaLitera = ssplit[tak + 1];
                if (floodowanyZnak == null && !split.equals("") && !nastepnaLitera.isEmpty() && split.equalsIgnoreCase(nastepnaLitera)) {
                    floodowanyZnak = nastepnaLitera;
                    flood++;
                } else if (floodowanyZnak != null && floodowanyZnak.equalsIgnoreCase(split)) {
                    flood++;
                } else {
                    floodowanyZnak = nastepnaLitera;
                    if (flood < 10) flood = 0;
                }
                tak++;
            } catch (Exception ignored) {}
        }
        return flood;
    }

    public static int containsCaps(String msg) {
        msg = msg.replaceAll(" ", "")
                .replaceAll("<@!?([0-9])*>", "")
                .replaceAll("([xX])", "").replaceAll("([dD])", "");
        int caps = 0;
        char[] split = msg.toCharArray();
        if (split.length < 5) return 0;

        for (char s : split) {
            if (!String.valueOf(s).equals("") && Character.isUpperCase(s)) {
                caps++;
            }
        }

        try {
            return ((caps / split.length) * 100);
        } catch (Exception e) {
            return 0;
        }

    }

    public static int emoteCount(String msg, JDA api) {
        int count = 0;
        Matcher m = EMOJI.matcher(msg);
        while (m.find()) {
            count++;
        }

        List<String> list = new ArrayList<>(Arrays.asList(msg.split(" ")));
        count += checkEmote(list, api);

        return count;
    }

    private static int checkEmote(List<String> list, JDA api) {
        int count = 0;
        for (String s : list) {
            if (Emoji.resolve(s, api) != null) count++;
        }
        return count;
    }

}
