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
import pl.kamil0024.core.database.config.Dao;
import pl.kamil0024.core.database.config.StatsConfig;

import java.util.List;

public class StatsDao implements Dao<StatsConfig> {

    private final PgMapper<StatsConfig> mapper;

    public StatsDao(DatabaseManager databaseManager) {
        if (databaseManager == null) throw new IllegalStateException("databaseManager == null");
        mapper = databaseManager.getPgStore().mapSync(StatsConfig.class);
    }

    @Override
    public StatsConfig get(String id) {
        return mapper.load(id).orElseGet(() -> new StatsConfig(id));
    }

    @Override
    public void save(StatsConfig toCos) {
        mapper.save(toCos);
    }

    @Override
    public List<StatsConfig> getAll() {
        return mapper.loadAll();
    }
}
