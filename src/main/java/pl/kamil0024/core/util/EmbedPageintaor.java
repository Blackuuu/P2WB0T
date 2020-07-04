package pl.kamil0024.core.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class EmbedPageintaor {
    
    private static final String FIRST_EMOJI = "\u23EE";
    private static final String LEFT_EMOJI = "\u25C0";
    private static final String RIGHT_EMOJI = "\u25B6";
    private static final String LAST_EMOJI = "\u23ED";
    private static final String STOP_EMOJI = "\u23F9";

    private final JDA api;
    private final EventWaiter eventWaiter;
    private final List<EmbedBuilder> pages;
    private int thisPage = 1;
    private boolean isPun;

    private Message botMsg;
    private long botMsgId;

    private long userId;

    public EmbedPageintaor(List<EmbedBuilder> pages, User user, EventWaiter eventWaiter, JDA api) {
        this.eventWaiter = eventWaiter;
        this.pages = pages;
        this.userId = user.getIdLong();
        this.api = api;
    }

    public EmbedPageintaor create(MessageChannel channel) {
        channel.sendMessage(render(1)).override(true).queue(msg -> {
            botMsg = msg;
            botMsgId = msg.getIdLong();
            if (pages.size() != 1) {
                addReactions(msg);
                waitForReaction();
            }
        });
        return this;
    }

    public EmbedPageintaor create(Message message) {
        message.editMessage(render(1)).override(true).queue(msg -> {
            botMsg = msg;
            botMsgId = msg.getIdLong();
            if (pages.size() != 1) {
                addReactions(msg);
                waitForReaction();
            }
        });
        return this;
    }

    private void waitForReaction() {
        eventWaiter.waitForEvent(MessageReactionAddEvent.class, this::checkReaction,
                this::onMessageReactionAdd, 60, TimeUnit.SECONDS, this::clearReactions);
    }

    private void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser().getIdLong() != userId) return;
        if (event.getMessageIdLong() != botMsgId) return;

        if (!event.getReactionEmote().isEmote()) {
            switch (event.getReactionEmote().getName()) {
                case FIRST_EMOJI:
                    thisPage = 1;
                    break;
                case LEFT_EMOJI:
                    if (thisPage > 1) thisPage--;
                    break;
                case RIGHT_EMOJI:
                    if (thisPage < pages.size()) thisPage++;
                    break;
                case LAST_EMOJI:
                    thisPage = pages.size();
                    break;
                case STOP_EMOJI:
                    botMsg.delete().queue();
                    return;
                default: return;
            }
        }
        try {
            event.getReaction().removeReaction(event.getUser()).queue();
        } catch (PermissionException ignored) { }
        botMsg.editMessage(render(thisPage)).override(true).complete();
        waitForReaction();
    }

    private void addReactions(Message message) {
        message.addReaction(FIRST_EMOJI).queue();
        message.addReaction(LEFT_EMOJI).queue();
        message.addReaction(RIGHT_EMOJI).queue();
        message.addReaction(LAST_EMOJI).queue();
        message.addReaction(STOP_EMOJI).queue();
    }

    private void clearReactions() {
        if (!isPun) {
            try {
                botMsg.clearReactions().complete();
            } catch (Exception ignored) {/*lul*/}
        }
    }

    private boolean checkReaction(MessageReactionAddEvent event) {
        if (event.getMessageIdLong() == botMsgId && !event.getReactionEmote().isEmote()) {
            switch (event.getReactionEmote().getName()) {
                case FIRST_EMOJI:
                case LEFT_EMOJI:
                case RIGHT_EMOJI:
                case LAST_EMOJI:
                case STOP_EMOJI:
                    return event.getUser().getIdLong() == userId;
                default:
                    return false;
            }
        }
        return false;
    }

    private MessageEmbed render(int page) {
        EmbedBuilder pageEmbed = pages.get(page - 1);
        pageEmbed.setFooter(String.format("%s/%s", page, pages.size()), null);
        return pageEmbed.build();
    }

    public EmbedPageintaor setPun(boolean bol) {
        isPun = bol;
        return this;
    }
    
}