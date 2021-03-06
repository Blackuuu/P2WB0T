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

package pl.kamil0024.status.statusy;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.RichPresence;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.Nullable;
import pl.kamil0024.chat.listener.ChatListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.undertow.server.handlers.SSLHeaderHandler.HTTPS;

public class WulgarneStatusy extends ListenerAdapter {

    private static final Pattern HTTP = Pattern.compile("([0-9a-z_-]+\\.)+(com|infonet|net|org|pro|de|ggmc|md|me|tt|tv|uk|us|uy|uz|va|vc|ve|vg|vi|vn|vu|wf|ws|ye|yt)");

    private final List<String> przeklenstwa;

    public WulgarneStatusy() {
        this.przeklenstwa = ChatListener.loadPrzeklenstwa();
    }

    public List<String> getAvtivity(Member mem) {
        List<String> list = new ArrayList<>();
        try {
            for (Activity act : mem.getActivities()) {
                list.add(act.getName());
                if (act.isRich()) {
                    RichPresence rp = act.asRichPresence();
                    try {
                        //noinspection ConstantConditions
                        list.add(rp.getState());
                        list.add(rp.getDetails());
                    } catch (NullPointerException ignored) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int xd = 0; xd < 5; xd++) {
            list.remove(null);
        }
        return list;
    }

    @Nullable
    public String containsLink(List<String> list) {
        for (String s : list) {
            if (s != null && !s.isEmpty() && !s.toLowerCase().contains("gram na")) {
                s = s.toLowerCase().replaceAll("derpmc.pl", "tak")
                        .replaceAll("feerko\\.pl", "tak")
                        .replaceAll("hajsmc\\.pl", "tak")
                        .replaceAll("roizy\\.pl", "tak")
                        .replaceAll("hypixel\\.net", "tak")
                        .replaceAll("blazingpack\\.pl", "tak")
                        .replaceAll("1\\.8\\.8", "tak")
                        .replaceAll("\\.by", "tak")
                        .replaceAll("blazingpack\\.pl", "tak");
                Matcher macher = HTTP.matcher(s);
                if (macher.find()) return s;
            }
        }
        return null;
    }

    @Nullable
    public String containsSwear(List<String> list) {
        for (String s : list) {
            if (s != null && !s.isEmpty()) {
                for (String split : s.toLowerCase().split(" ")) {
                    if (przeklenstwa.contains(split)) return split;
                }
            }
        }
        return null;
    }

}
