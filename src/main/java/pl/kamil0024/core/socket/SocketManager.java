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

package pl.kamil0024.core.socket;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import pl.kamil0024.core.command.CommandContext;
import pl.kamil0024.core.command.CommandExecute;
import pl.kamil0024.core.logger.Log;
import pl.kamil0024.core.socket.actions.*;
import pl.kamil0024.core.util.EmbedPageintaor;
import pl.kamil0024.core.util.EventWaiter;
import pl.kamil0024.core.util.GsonUtil;
import pl.kamil0024.music.commands.PlayCommand;
import pl.kamil0024.music.commands.privates.PrivateQueueCommand;

import java.util.*;

@SuppressWarnings("unused")
public class SocketManager {

    @Getter private final HashMap<Integer, SocketClient> clients;
    @Getter private final HashMap<Integer, String> botIds;

    private final ShardManager api;
    private final EventWaiter eventWaiter;

    public SocketManager(AsyncEventBus eventBus, ShardManager api, EventWaiter eventWaiter) {
        clients = new HashMap<>();
        botIds = new HashMap<>();
        eventBus.register(this);
        this.api = api;
        this.eventWaiter = eventWaiter;
    }

    @Subscribe
    public void retrieveMessage(SocketServer.SocketJson socketJson) {
        Log.debug("Nowa wiadomość socketa %s:", socketJson.getId());
        Log.debug(socketJson.getJson());
        if (socketJson.getJson().startsWith("setBotId:")) {
            String id = socketJson.getJson().split("setBotId: ")[1];
            clients.get(socketJson.getId()).setBotId(id);
            botIds.put(socketJson.getId(), id);
            return;
        } else if (socketJson.getJson().startsWith("setChannel:")) {
            String channelId = socketJson.getJson().split("setChannel: ")[1];
            if (channelId.equals("null")) clients.get(socketJson.getId()).setVoiceChannel(null);
            else clients.get(socketJson.getId()).setVoiceChannel(channelId);
            return;
        }

        try {
            Response response;
            try {
                response = SocketServer.GSON.fromJson(socketJson.getJson(), Response.class);
            } catch (Exception e) {
                Log.newError("Nie udało się zamienić tekstu na JSONa!\nJSON: " + socketJson.getJson(), getClass());
                return;
            }

            if (response.getAction().getTopic().equals("shutdown")) return;

            if (response.getAction().getTopic().equals("queueupdate")) {
                if (!response.isSuccess()) {
                    Log.error("Nie udało się aktualizować kolejki!");
                    Log.error(SocketServer.GSON.toJson(socketJson));
                    return;
                }
                SocketClient socket = clients.get(response.getAction().getSocketId());
                String[] queueListData = SocketServer.GSON.fromJson(socketJson.getJson(), String[].class);
                socket.setTracksList(Arrays.asList(queueListData));
                Log.debug("Queue socketa wynosi teraz: " + socketJson.getJson());
                return;
            }

            TextChannel txt = api.getTextChannelById(response.getAction().getChannelId());
            if (txt == null) throw new NullPointerException("Kanal jakims cudem jest nullem");
            String ping = String.format("<@%s>", response.getAction().getMemberId());
            if (!response.isSuccess()) {
                Message msg = txt.sendMessage(ping + ", " + response.getErrorMessage()).complete();
                msg.addReaction(CommandExecute.getReaction(msg.getAuthor(), false)).queue();
                return;
            }

            if (!response.getAction().isSendMessage()) return;

            switch (response.getMessageType()) {
                case "message": {
                    if (response.getData() == null) return;
                    Message msg = txt.sendMessage(ping + ", " + response.getData()).complete();
                    msg.addReaction(CommandExecute.getReaction(msg.getAuthor(), true)).queue();
                    break;
                }
                case "embedtrack": {
                    PrivateQueueCommand.Track t = GsonUtil.fromJSON(GsonUtil.toJSON(response.getData()), PrivateQueueCommand.Track.class);
                    EmbedBuilder track = new PrivateQueueCommand.DecodeTrack(t, false).create();
                    MessageBuilder mb = new MessageBuilder();
                    mb.setContent(ping + ", dodano do kolejki!");
                    mb.setEmbed(track.build());
                    Message msg = txt.sendMessage(mb.build()).complete();
                    msg.addReaction(CommandExecute.getReaction(msg.getAuthor(), true)).queue();
                    break;
                }
                case "queuelist":
                    String data = GsonUtil.toJSON(response.getData());
                    Iterator<Object> a = new JSONArray(data).iterator();
                    List<EmbedBuilder> tracks = new ArrayList<>();
                    boolean first = true;
                    while (a.hasNext()) {
                        tracks.add(new PrivateQueueCommand.DecodeTrack(a.next().toString(), first).create());
                        first = false;
                    }
                    new EmbedPageintaor(tracks, Objects.requireNonNull(api.getUserById(response.getAction().getMemberId())), eventWaiter, api.getShards().get(0))
                            .create(txt);

                    break;
            }

        } catch (Exception e) {
            Log.newError("Wystapil blad podczas przetwarzania wiadomosci od socketa! W logach masz wiecej informacji\nJSON: " + socketJson.getJson(), getClass());
            Log.newError(e, getClass());
        }

    }

    public synchronized void sendMessage(SocketAction socketAction) {
        SocketClient client = clients.get(socketAction.getSocketId());
        if (client == null) {
            Log.newError("Próbowano wysłać wiadomość do socketa %s, ale ten nie istnieje!", getClass(), socketAction.getSocketId());
            return;
        }
        client.getWriter().println(socketAction.toJson());
    }

    @Subscribe
    public void disconnectEvent(SocketServer.SocketDisconnect socketDisconnect) {
        Log.debug("Odłączono socketa %s", socketDisconnect.getId());
        clients.remove(socketDisconnect.getId());
        botIds.remove(socketDisconnect.getId());
    }

    public Action getAction(String memberId, String channelId, int socketId) {
        return new Action(this, memberId, channelId, socketId, true);
    }

    @Nullable
    public SocketClient getClientFromId(String userId) {
       return clients.values().stream().filter(s -> s.getBotId().equals(userId)).findAny().orElse(null);
    }

    @Nullable
    public SocketClient getClientFromChanne(CommandContext context) {
        for (Member member : PlayCommand.getVc(context.getMember()).getMembers()) {
            if (member.getUser().isBot()) {
                SocketClient agent = getClientFromId(member.getId());
                if (agent != null) return agent;
            }
        }
        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    @AllArgsConstructor
    public static class Action {
        private final SocketManager manager;
        private final String memberId;
        private final String channelId;
        private final int socketId;

        private Boolean sendMessage;

        public Action connect(String voiceChannelId) {
            manager.sendMessage(new ConnectAction(sendMessage, memberId, channelId, socketId, voiceChannelId));
            return this;
        }
        public Action disconnect() {
            manager.sendMessage(new DisconnectAction(sendMessage, memberId, channelId, socketId));
            return this;
        }
        public Action play(String track) {
            manager.sendMessage(new PlayAction(sendMessage, memberId, channelId, socketId, track));
            return this;
        }
        public Action queue() {
            manager.sendMessage(new QueueAction(sendMessage, memberId, channelId, socketId));
            return this;
        }
        public Action shutdown() {
            manager.sendMessage(new ShutdownAction(sendMessage, memberId, channelId, socketId));
            return this;
        }
        public Action skip() {
            manager.sendMessage(new SkipAction(sendMessage, memberId, channelId, socketId));
            return this;
        }

        public Action setSendMessage(boolean bol) {
            sendMessage = bol;
            return this;
        }

        public boolean getSendMessage() {
            return this.sendMessage;
        }

    }

    @Data
    @AllArgsConstructor
    public static class Response {
        private final SocketAction action;
        private final boolean success;
        private final String errorMessage;
        private final String messageType;
        private final Object data;

        @Data
        public static class SocketAction {
            public SocketAction() { }

            private boolean sendMessage;
            private String memberId;
            private String channelId;
            private String topic;
            private int socketId;
            private Map<String, Object> args;

        }

    }

}
