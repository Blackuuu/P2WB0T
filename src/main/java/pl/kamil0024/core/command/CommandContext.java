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

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.gson.Gson;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.Nullable;
import pl.kamil0024.core.Ustawienia;
import pl.kamil0024.core.arguments.Args;
import pl.kamil0024.core.arguments.ArgumentManager;
import pl.kamil0024.core.util.Tlumaczenia;
import pl.kamil0024.core.util.UserUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@SuppressWarnings({"unused", "UnusedReturnValue", "StringBufferMayBeStringBuilder"})
public class CommandContext {

    @Getter private final MessageReceivedEvent event;
    @Getter private final String prefix;
    @Getter private final HashMap<Integer, String> args;

    private final ArgumentManager argumentManager;
    private final Command cmd;
    private final Tlumaczenia tlumaczenia;

    private static final Pattern URLPATTERN = Pattern.compile("(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\." +
            "[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]" +
            "\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]\\.[^\\s]{2,})");

    public CommandContext(MessageReceivedEvent event, String prefix, @Nullable HashMap<Integer, String> args, Tlumaczenia tlumaczenia, ArgumentManager argumentManager, Command cmd) {
        this.event = event;
        this.prefix = prefix;
        this.args = args;
        this.tlumaczenia = tlumaczenia;
        this.argumentManager = argumentManager;
        this.cmd = cmd;
    }

    public Command getCommand() {
        return cmd;
    }

    @Nullable
    public Object getParsedArgument(String argument, String value, CommandContext context) {
        Args a = argumentManager.getArgument(argument);
        if (a == null) throw new IllegalArgumentException("Nie ma takiego argumentu jak " + argument);
        try {
            return a.parsed(value, event.getJDA(), context);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Przy uzyskiwaniu parseda od argumenta " + a.getName() + " wystapil blad!");
        }
    }

    public User getSender() {
        return event.getAuthor();
    }

    public User getUser() {
        return getSender();
    }

    public Member getMember() {
        return event.getMember();
    }

    public SelfUser getBot() {
        return event.getJDA().getSelfUser();
    }

    public Message getMessage() {
        return event.getMessage();
    }

    public TextChannel getChannel() {
        return event.getTextChannel();
    }

    public Guild getGuild() {
        return event.getGuild();
    }

    public MessageAction send(String msg) {
        return send(msg, true);
    }

    public MessageAction send(CharSequence msg, boolean checkUrl) {
        String message = String.valueOf(msg);
        if (checkUrl && URLPATTERN.matcher(msg).matches()) {
            message = message.replaceAll(String.valueOf(URLPATTERN), "[LINK]");
        }
        return event.getChannel().sendMessage(message.replaceAll("@(everyone|here)", "@\u200b$1")).referenceById(getMessage().getIdLong());
    }

    public MessageAction send(MessageEmbed message) {
        return event.getChannel().sendMessage(message).referenceById(getMessage().getIdLong());
    }

    public MessageAction sendTranslate(String key, Object... obj) {
        return send(getTranslate(key, obj));
    }

    public MessageAction sendTranslate(String key) {
        return send(getTranslate(key));
    }

    @CheckReturnValue
    public String getTranslate(String msg) {
        return tlumaczenia.get(msg);
    }

    @CheckReturnValue
    public String getTranslate(String key, String... argi) {
        return tlumaczenia.get(key, argi);
    }

    @CheckReturnValue
    public String getTranslate(String key, Object... argi) {
        ArrayList<String> parsedArgi = new ArrayList<>();
        for (Object arg : argi) {
            parsedArgi.add(arg.toString());
        }
        return tlumaczenia.get(key, parsedArgi.toArray(new String[]{}));
    }

    public String getArgsToString(Integer num) {
        StringBuilder args = new StringBuilder();
        int size = 1;
        for (Map.Entry<Integer, String> a : getArgs().entrySet()) {
            if (a.getKey() >= num) {
                args.append(a.getValue());
            }
            if (size < getArgs().size()) args.append(" ");
            size++;
        }
        if (args.toString().isEmpty()) return null;
        String st = args.toString();
        if (st.startsWith(" ")) return st.substring(1);
        return st;
    }

    public ParsedArgumenty getParsed() {
        return new ParsedArgumenty(this);
    }

    public JDA getJDA() {
        return event.getJDA();
    }

    public ShardManager getShardManager() {
        return event.getJDA().getShardManager();
    }

    public boolean executedInRekru() {
        return event.getGuild().getId().equals(Ustawienia.instance.rekrutacyjny.guildId);
    }

    @Override
    public String toString() {
        return "user=" + UserUtil.getName(getUser()) + " " +
                "msg=" + getMessage().getContentRaw() + " " +
                "args=" + new Gson().toJson(getArgs());
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    public class ParsedArgumenty {

        private final CommandContext context;

        public ParsedArgumenty(CommandContext context) {
            this.context = context;
        }

        @Nullable
        public User getUser(String user) {
            return (User) context.getParsedArgument("user", user, context);
        }

        @Nullable
        public Member getMember(String member) {
            return (Member) context.getParsedArgument("member", member, context);
        }

        @Nullable
        public TextChannel getTextChannel(@Nullable String channel) {
            if (channel == null) return null;
            return (TextChannel) context.getParsedArgument("textchannel", channel, context);
        }

        @Nullable
        public Integer getNumber(@Nullable String num) {
            if (num == null) return null;
            Integer n = null;
            try {
                n = Integer.parseInt(num);
            } catch (NumberFormatException ignored) { }
            return n;
        }

        @Nullable
        public Double getDouble(@Nullable String num) {
            if (num == null) return null;
            Double n = null;
            try {
                n = Double.parseDouble(num);
            } catch (Exception ignored) { }
            return n;
        }

    }
}