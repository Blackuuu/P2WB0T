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

package pl.kamil0024.moderation;

import pl.kamil0024.core.command.Command;
import pl.kamil0024.core.command.CommandManager;
import pl.kamil0024.core.database.CaseDao;
import pl.kamil0024.core.database.MultiDao;
import pl.kamil0024.core.database.NieobecnosciDao;
import pl.kamil0024.core.module.Modul;
import pl.kamil0024.core.util.EventWaiter;
import pl.kamil0024.core.util.kary.KaryJSON;
import pl.kamil0024.moderation.commands.*;
import pl.kamil0024.moderation.listeners.ModLog;
import pl.kamil0024.nieobecnosci.NieobecnosciManager;
import pl.kamil0024.stats.StatsModule;

import java.util.ArrayList;

public class ModerationModule implements Modul {

    private final CommandManager commandManager;
    private final EventWaiter eventWaiter;
    private final CaseDao caseDao;
    private final StatsModule statsModule;
    private final NieobecnosciDao nieobecnosciDao;
    private final NieobecnosciManager nieobecnosciManager;
    private final ModLog modLog;
    private final KaryJSON karyJSON;
    private final MultiDao multiDao;

    private ArrayList<Command> cmd;
    private boolean start = false;

    public ModerationModule(CommandManager commandManager, EventWaiter eventWaiter, CaseDao caseDao, StatsModule statsModule, NieobecnosciManager nieobecnosciManager, NieobecnosciDao nieobecnosciDao, ModLog modLog, KaryJSON karyJSON, MultiDao multiDao) {
        this.commandManager = commandManager;
        this.eventWaiter = eventWaiter;
        this.caseDao = caseDao;
        this.statsModule= statsModule;
        this.nieobecnosciManager = nieobecnosciManager;
        this.nieobecnosciDao = nieobecnosciDao;
        this.modLog = modLog;
        this.karyJSON = karyJSON;
        this.multiDao = multiDao;
    }

    @Override
    public boolean startUp() {
        cmd = new ArrayList<>();

        cmd.add(new StatusCommand(eventWaiter));
        cmd.add(new KarainfoCommand(caseDao, eventWaiter));
        cmd.add(new UnmuteCommand(caseDao, modLog));
        cmd.add(new TempmuteCommand(caseDao, modLog, statsModule));
        cmd.add(new PunishCommand(karyJSON, eventWaiter, caseDao, modLog, statsModule));
        cmd.add(new KickCommand(caseDao, modLog, statsModule));
        cmd.add(new BanCommand(caseDao, modLog, statsModule));
        cmd.add(new TempbanCommand(caseDao, modLog, statsModule));
        cmd.add(new UnbanCommand(caseDao, modLog));
        cmd.add(new MuteCommand(caseDao, modLog, statsModule));
        cmd.add(new HistoryCommand(caseDao, eventWaiter));
        cmd.add(new NieobecnoscCommand(nieobecnosciManager, eventWaiter, nieobecnosciDao));
        cmd.add(new DowodCommand(caseDao, eventWaiter));
        cmd.add(new ClearCommand(statsModule));
        cmd.add(new CheckCommand(caseDao, eventWaiter));
        cmd.add(new MultiCommand(multiDao, eventWaiter));

        cmd.forEach(commandManager::registerCommand);
        setStart(true);
        return true;
    }

    @Override
    public boolean shutDown() {
        commandManager.unregisterCommands(cmd);
        setStart(false);
        return true;
    }

    @Override
    public String getName() {
        return "moderation";
    }

    @Override
    public boolean isStart() {
        return start;
    }

    @Override
    public void setStart(boolean bol) {
        this.start = bol;
    }

}
