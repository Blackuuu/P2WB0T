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
import lombok.Getter;
import pl.kamil0024.core.logger.Log;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class SocketClient extends Thread {

    private final Socket socket;
    private final AsyncEventBus eventBus;

    @Getter private PrintWriter writer;
    @Getter private final int socketId;

    public SocketClient(Socket socket, AsyncEventBus eventBus) {
        this.socket = socket;
        this.socketId = new Random().nextInt(Integer.MAX_VALUE);
        this.eventBus = eventBus;

        try {
            OutputStream output = socket.getOutputStream();
            this.writer = new PrintWriter(output, true);
        } catch (Exception e) {
            Log.newError(e, getClass());
        }

    }

    @Override
    public void start() {
        try {
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            String text;
            while ((text = reader.readLine()) != null) {
                eventBus.post(new SocketServer.SocketJson(getSocketId(), text));
            }
            eventBus.post(new SocketServer.SocketDisconnect(getSocketId()));
        } catch (Exception e) {
            Log.newError(e, getClass());
        }
    }

}
