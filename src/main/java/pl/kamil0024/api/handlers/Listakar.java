package pl.kamil0024.api.handlers;

import com.google.inject.Inject;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import pl.kamil0024.api.APIModule;
import pl.kamil0024.api.Response;
import pl.kamil0024.core.database.CaseDao;
import pl.kamil0024.core.database.config.CaseConfig;
import pl.kamil0024.core.logger.Log;

import java.util.ArrayList;
import java.util.List;

public class Listakar implements HttpHandler {

    @Inject private CaseDao caseDao;
    @Inject private APIModule api;

    public Listakar(CaseDao caseDao, APIModule apiModule) {
        this.caseDao = caseDao;
        this.api = apiModule;
    }


    @Override
    public void handleRequest(HttpServerExchange ex) throws Exception {
        if (!CheckToken.checkToken(ex)) return;

        String nick = ex.getQueryParameters().get("nick").getFirst();
        if (nick.isEmpty()) {
            Response.sendErrorResponse(ex,"Zły nick", "Nick jest pusty?");
            return;
        }

        try {
            List<CaseConfig> kary = new ArrayList<>();
            caseDao.getAllNick(nick).forEach(ccase -> {
                CaseConfig formated = Karainfo.format(ccase, api);
                Log.info("Dodaje " + formated.getId());
                kary.add(formated);
            });
            if (kary.isEmpty()) {
                Response.sendErrorResponse(ex,"Zły nick", "Ten nick nie ma żadnej kary");
                return;
            }

            Response.sendObjectResponse(ex, kary);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
