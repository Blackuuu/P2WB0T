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

package pl.kamil0024.core.database;

import gg.amy.pgorm.PgMapper;
import net.dv8tion.jda.api.entities.Member;
import pl.kamil0024.core.database.config.Dao;
import pl.kamil0024.core.database.config.TicketConfig;
import pl.kamil0024.core.util.BetterStringBuilder;
import pl.kamil0024.core.util.UserUtil;

import java.util.List;

public class TicketDao implements Dao<TicketConfig> {

    private final PgMapper<TicketConfig> mapper;

    public TicketDao(DatabaseManager databaseManager) {
        if (databaseManager == null) throw new IllegalStateException("databaseManager == null");
        mapper = databaseManager.getPgStore().mapSync(TicketConfig.class);
    }

    @Override
    public TicketConfig get(String id) {
        return mapper.load(id).orElseGet(() -> new TicketConfig(id));
    }

    @Override
    public void save(TicketConfig toCos) {
        mapper.save(toCos);
    }

    @Override
    public List<TicketConfig> getAll() {
        return mapper.loadAll();
    }

    public List<TicketConfig> getById(String id) {
        return mapper.getTicketById(id);
    }

    public List<TicketConfig> getByNick(String nick) {
        return mapper.getTicketByNick(nick);
    }

    public synchronized TicketConfig getByRandomId() {
        int n = 12;
        String alphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvxyz";

        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            int index
                    = (int)(alphaNumericString.length()
                    * Math.random());
            sb.append(alphaNumericString
                    .charAt(index));
        }
        return new TicketConfig(sb.toString());
    }

    public void sendMessage(Member member, String admId) {
        try {
            TicketConfig tc = getByRandomId();
            tc.setAdmId(admId);
            tc.setUserId(member.getId());
            String nick = UserUtil.getMcNick(member);
            tc.setUserNick(nick.equals("-") ? null : nick);

            BetterStringBuilder msg = new BetterStringBuilder();
            msg.appendLine("Cześć,\n");
            msg.appendLine("Twoja prośba o pomoc w naszym nowym systemie właśnie została zakończona. " +
                    "Bylibyśmy wdzięczni, gdybyś poświęcił chwilę nad uzupełnieniem ankiety znajdującej się tutaj: " +
                    tc.getUrl());
            msg.appendLine("\n\nDziękujemy za wszystkie opinie i chęć polepszania systemu!");

            save(tc);
            member.getUser().openPrivateChannel().complete().sendMessage(msg.toString()).complete();
        } catch (Exception ignored) { }
    }

}