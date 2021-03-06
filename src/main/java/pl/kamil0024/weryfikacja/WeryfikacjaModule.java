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

package pl.kamil0024.weryfikacja;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import pl.kamil0024.api.APIModule;
import pl.kamil0024.bdate.BDate;
import pl.kamil0024.core.command.enums.PermLevel;
import pl.kamil0024.core.util.UserUtil;
import pl.kamil0024.moderation.listeners.ModLog;
import pl.kamil0024.core.Ustawienia;
import pl.kamil0024.core.database.CaseDao;
import pl.kamil0024.core.database.MultiDao;
import pl.kamil0024.core.database.WeryfikacjaDao;
import pl.kamil0024.core.database.config.DiscordInviteConfig;
import pl.kamil0024.core.database.config.MultiConfig;
import pl.kamil0024.core.database.config.WeryfikacjaConfig;
import pl.kamil0024.core.module.Modul;
import pl.kamil0024.core.util.Nick;
import pl.kamil0024.status.listeners.ChangeNickname;
import pl.kamil0024.weryfikacja.listeners.CheckMk;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class WeryfikacjaModule extends ListenerAdapter implements Modul {

    public final APIModule apiModule;
    private final MultiDao multiDao;
    private final ModLog modLog;
    private final CaseDao caseDao;
    private final WeryfikacjaDao weryfikacjaDao;

    private boolean start = false;
    private ChangeNickname changeNickname;

    public WeryfikacjaModule(APIModule apiModule, MultiDao multiDao, ModLog modLog, CaseDao caseDao, WeryfikacjaDao weryfikacjaDao) {
        this.apiModule = apiModule;
        this.multiDao = multiDao;
        this.modLog = modLog;
        this.caseDao = caseDao;
        this.weryfikacjaDao = weryfikacjaDao;
    }

    @Override
    public boolean startUp() {
        this.changeNickname = new ChangeNickname();
        apiModule.getApi().addEventListener(this, changeNickname);
        return true;
    }

    @Override
    public boolean shutDown() {
        apiModule.getApi().removeEventListener(this, changeNickname);
        return true;
    }

    @Override
    public String getName() {
        return "weryfikacja";
    }

    @Override
    public boolean isStart() {
        return start;
    }

    @Override
    public void setStart(boolean bol) {
        this.start = bol;
    }

    public void executeCode(Member member, MessageChannel channel, String code, Guild g) {
        DiscordInviteConfig dc = apiModule.getDiscordConfig(code);
        if (dc == null) {
            channel.sendMessage(member.getAsMention() + ", podałeś zły kod! Sprawdź swój kod jeszcze raz na serwerze lub wygeneruj nowy.")
                    .queue(m -> m.delete().queueAfter(11, TimeUnit.SECONDS));
            return;
        }
        executeCode(member.getId(), dc, channel, g);
    }

    public void executeCode(String userId, DiscordInviteConfig config, MessageChannel channel, Guild g) {

        Member member = g.getMemberById(userId);
        if (member == null) return;

        WeryfikacjaConfig wc = weryfikacjaDao.get(config.getNick());
        if (wc != null && !wc.getDiscordId().equals(userId)) {
            channel.sendMessage(member.getAsMention() + " nick, na którym próbujesz wejść ma już przypisane konto Discord. Jedno konto Minecraft może być przypisane **tylko** do jednego konta Discord! Jeżeli straciłeś/aś dostęp do starego konta, napisz do nas!")
                    .queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS));
            apiModule.getDcCache().invalidate(config.getKod());
            return;
        }

        Role ranga = null;
        String nickname = null;

        switch (config.getRanga()) {
            case "Gracz":
                ranga = g.getRoleById(Ustawienia.instance.rangi.gracz);
                nickname = "";
                break;
            case "VIP":
                ranga = g.getRoleById(Ustawienia.instance.rangi.vip);
                break;
            case "VIP+":
                ranga = g.getRoleById(Ustawienia.instance.rangi.vipplus);
                break;
            case "MVP":
                ranga = g.getRoleById(Ustawienia.instance.rangi.mvp);
                break;
            case "MVP+":
                ranga = g.getRoleById(Ustawienia.instance.rangi.mvpplus);
                break;
            case "MVP++":
                ranga = g.getRoleById(Ustawienia.instance.rangi.mvpplusplus);
                break;
            case "Sponsor":
                ranga = g.getRoleById(Ustawienia.instance.rangi.sponsor);
                break;
            case "MiniYT":
                ranga = g.getRoleById(Ustawienia.instance.rangi.miniyt);
                nickname = "[MiniYT]";
                break;
            case "YouTuber":
                ranga = g.getRoleById(Ustawienia.instance.rangi.yt);
                nickname = "[YT]";
                break;
            case "Pomocnik":
                ranga = g.getRoleById(Ustawienia.instance.rangi.pomocnik);
                nickname = "[POM]";
                break;
            case "Stażysta":
                ranga = g.getRoleById(Ustawienia.instance.rangi.stazysta);
                nickname = "[STAŻ]";
                break;
            case "Build_Team":
                ranga = g.getRoleById(Ustawienia.instance.rangi.buildteam);
                nickname = "[BUILD TEAM]";
        }

        if (ranga == null) {
            channel.sendMessage(member.getAsMention() + ", twoja ranga została źle wpisana! Skontaktuj się z kimś z administracji")
                    .queue(m -> m.delete().queueAfter(8, TimeUnit.SECONDS));
            return;
        }

        if (nickname == null) nickname = "[" + ranga.getName().toUpperCase() + "]";

        try {
            g.addRoleToMember(member, ranga).complete();
        } catch (Exception e) {
            channel.sendMessage(member.getAsMention() + ", nie udało się nadać rangi. Spróbuj ponownie! Jeżeli błąd będzie się powtarzał, powiadom administracje")
                    .queue(m -> m.delete().queueAfter(8, TimeUnit.SECONDS));
            return;
        }

        try {
            g.modifyNickname(member, nickname + " " + config.getNick()).complete();
        } catch (Exception ignored) {}

        MultiConfig conf = multiDao.get(member.getId());
        conf.getNicki().add(new Nick(nickname + " " + config.getNick(), new BDate().getTimestamp()));
        multiDao.save(conf);

        modLog.checkKara(member, true,
                caseDao.getNickAktywne(config.getNick().replace(" ", "")));

        channel.sendMessage(member.getAsMention() + ", pomyślnie zweryfikowano. Witamy na serwerze sieci P2W!")
                .allowedMentions(Collections.singleton(Message.MentionType.USER))
                .queue(m -> m.delete().queueAfter(8, TimeUnit.SECONDS));

        if (ranga.getName().equalsIgnoreCase("gracz")) {
            CheckMk mk = new CheckMk(member);
            mk.check();
        }

        WeryfikacjaConfig werc = new WeryfikacjaConfig(config.getNick());
        werc.setDiscordId(member.getId());
        werc.setTime(new Date().getTime());
        weryfikacjaDao.save(werc);

        apiModule.getDcCache().invalidate(config.getKod());
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!event.getChannel().getId().equals("740157959207780362") || event.getAuthor().isBot() || !event.isFromGuild()) return;

        String msg = event.getMessage().getContentRaw();
        try {
            event.getMessage().delete().complete();
        } catch (Exception ignored) { }

        executeCode(event.getMember(), event.getChannel(), msg, event.getGuild());
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (!event.getChannel().getId().equals("740157959207780362") || event.getUser().isBot() || !event.isFromGuild()) return;

        try {
            if (UserUtil.getPermLevel(event.getMember()).getNumer() < PermLevel.MODERATOR.getNumer()) {
                event.getReaction().removeReaction().queue();
            }
        } catch (Exception ignored) { }

        DiscordInviteConfig conf = apiModule.getNewWery().getIfPresent(event.getUserId());
        if (conf == null) return;

        executeCode(event.getUserId(), conf, event.getChannel(), event.getGuild());
        apiModule.getNewWery().invalidate(event.getUserId());
    }

}
