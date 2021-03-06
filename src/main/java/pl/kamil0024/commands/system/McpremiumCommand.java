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

package pl.kamil0024.commands.system;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pl.kamil0024.core.command.Command;
import pl.kamil0024.core.command.CommandContext;
import pl.kamil0024.core.util.NetworkUtil;
import pl.kamil0024.core.util.UsageException;
import pl.kamil0024.core.util.UserUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class McpremiumCommand extends Command {

    public McpremiumCommand() {
        name = "mcpremium";
        cooldown = 10;
        enabledInRekru = true;
    }

    @Override
    public boolean execute(@NotNull CommandContext context) {
        String name = null, uuid = null;
        String arg = context.getArgs().get(0);
        List<String> listaNazw = new ArrayList<>();
        if (arg == null) throw new UsageException();
        try {
            JSONObject jOb = NetworkUtil.getJson("https://api.mojang.com/users/profiles/minecraft/" + NetworkUtil.encodeURIComponent(arg));
            uuid = Objects.requireNonNull(jOb).getString("id");
            name = Objects.requireNonNull(jOb).getString("name");

            JSONArray lista = NetworkUtil.getJsonArray("https://api.mojang.com/user/profiles/" + uuid + "/names");
            for (Object tfu : Objects.requireNonNull(lista)) {
                JSONObject obj = (JSONObject) tfu;
                StringBuilder sb = new StringBuilder();
                sb.append(obj.getString("name"));
                if (obj.has("changedToAt")) {
                    long timestamp = obj.getLong("changedToAt");
                    Date zmienioneO = new Date(timestamp);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy '@' HH:mm z");
                    sb.append(" ").append("ustawiony ").append(sdf.format(zmienioneO));
                }
                listaNazw.add(sb.toString());
            }
            Collections.reverse(listaNazw);
            String xd = listaNazw.remove(0);
            StringBuilder tekstPierw = new StringBuilder();
            StringBuilder tekstDalej = new StringBuilder();
            for (int i = 0; i < xd.split(" ").length; i++) {
                if (i == 0)
                    tekstPierw.append((xd.split(" ")[i]).replaceAll("_", "\\_"));
                else {
                    tekstDalej.append((xd.split(" ")[i]).replaceAll("_", "\\_"));
                    if (i + 1 < xd.split(" ").length) tekstDalej.append(" ");
                }
            }
            listaNazw.add(0, "**" + tekstPierw.toString() + "** " + tekstDalej.toString());
        } catch (JSONException | IOException ignored) { }

        if (name == null || uuid == null) {
            context.send(context.getTranslate("mcpremium.alex", context.getMember().getAsMention())).queue();
            return false;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(UserUtil.getColor(context.getMember()));
        eb.addField(context.getTranslate("mcpremium.name"), MarkdownSanitizer.escape(name), false);
        eb.addField(context.getTranslate("mcpremium.uuid"), formatUuid(Objects.requireNonNull(uuid)), false);
        eb.addField(context.getTranslate("mcpremium.namemc"), "[namemc.com](https://namemc.com/profile/" + uuid + ")", false);
        eb.setFooter(context.getTranslate("mcpremium.info", UserUtil.getName(context.getMember().getUser()), UserUtil.getMcNick(context.getMember())));
        if (listaNazw.size() > 1 && String.join("\n", listaNazw).length() < 1024) {
            eb.addField(context.getTranslate("mcpremium.nick"), String.join("\n", listaNazw),
                    false);
        }
        eb.setThumbnail("https://minotar.net/avatar/" + name + "/2048.png");
        eb.setImage("https://minotar.net/body/" + name + "/124.png");
        context.send(eb.build()).queue();
        return true;
    }

    private String formatUuid(String uuid) {
        int chars = 0;
        int pass = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : uuid.toCharArray()) {
            chars++;
            sb.append(c);
            if (pass == 0 && chars == 8) {
                sb.append('-');
                pass++;
                chars = 0;
            }
            if (pass == 1 && chars == 4) {
                sb.append('-');
                pass++;
                chars = 0;
            }
            if (pass == 2 && chars == 4) {
                sb.append('-');
                pass++;
                chars = 0;
            }
            if (pass == 3 && chars == 4) {
                sb.append('-');
                pass++;
                chars = 0;
            }
            if (pass == 4 && chars == 12) {
                sb.append('-');
                pass++;
                chars = 0;
            }
        }
        return UUID.fromString(sb.toString()).toString();
    }

}
