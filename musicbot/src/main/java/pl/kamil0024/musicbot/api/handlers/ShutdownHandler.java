package pl.kamil0024.musicbot.api.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import net.dv8tion.jda.api.sharding.ShardManager;
import pl.kamil0024.musicbot.api.Response;

public class ShutdownHandler implements HttpHandler {

    private ShardManager api;

    public ShutdownHandler(ShardManager api) {
        this.api = api;
    }

    @Override
    public void handleRequest(HttpServerExchange ex) {
        try {
            Response.sendResponse(ex, "Pomyślnie podłączono");
        } catch (Exception e) {
            Response.sendErrorResponse(ex, "Błąd", "Wystąpił błąd: " + e.getLocalizedMessage());
        }
        new Thread(() -> System.exit(0)).start();

    }

}
