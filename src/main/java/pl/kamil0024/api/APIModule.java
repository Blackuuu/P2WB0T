package pl.kamil0024.api;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import pl.kamil0024.api.handlers.CheckToken;
import pl.kamil0024.api.handlers.Karainfo;
import pl.kamil0024.api.handlers.Listakar;
import pl.kamil0024.api.internale.MiddlewareBuilder;
import pl.kamil0024.core.Ustawienia;
import pl.kamil0024.core.database.CaseDao;
import pl.kamil0024.core.database.config.UserConfig;
import pl.kamil0024.core.database.config.UserinfoConfig;
import pl.kamil0024.core.logger.Log;
import pl.kamil0024.core.module.Modul;
import pl.kamil0024.core.redis.Cache;
import pl.kamil0024.core.redis.RedisManager;
import pl.kamil0024.core.util.UserUtil;

import static io.undertow.Handlers.path;

@Getter
@SuppressWarnings("DanglingJavadoc")
public class APIModule implements Modul {

    private ShardManager api;
    private boolean start = false;
    Undertow server;

    @Inject private CaseDao caseDao;
    @Inject private RedisManager redisManager;

    private final Cache<UserinfoConfig> ucCache;

    private final Guild guild;

    public APIModule(ShardManager api, CaseDao caseDao, RedisManager redisManager) {
        this.api = api;
        this.redisManager = redisManager;
        this.guild = api.getGuildById(Ustawienia.instance.bot.guildId);
        if (guild == null) throw new UnsupportedOperationException("Gildia docelowa jest nullem!");

        this.caseDao = caseDao;

        this.ucCache = redisManager.new CacheRetriever<UserinfoConfig>(){}.getCache();
    }

    @Override
    public boolean startUp() {
        Gson gson = new Gson();
        RoutingHandler routes = new RoutingHandler();

        /**
         * @api {get} api/checkToken/:token Sprawdza token
         * @apiName checkToken
         * @apiDescription Używane do sprawdzenia prawidłowości tokenów
         * @apiGroup Token
         * @apiVersion 1.0.0
         * @apiParam {Token} token Token
         *
         * @apiSuccess {String} success Czy zapytanie się udało
         * @apiSuccess {String} msg Wiadomość potwierdzająca
         *
         * @apiError {Boolean} success Czy zapytanie się udało
         * @apiError {Object} error Odpowiedź
         * @apiError {Boolean} error.body Krótka odpowiedź błędu
         * @apiError {Boolean} error.description Długa odpowiedź błędu
         * @apiSuccessExample {json}
         *     HTTP/1.1 200 OK
         *     {
         *         "success": true,
         *         "msg": "Token jest dobry"
         *     }
         * @apiErrorExample {json}
         *     HTTP/1.1 200 OK
         *     {
         *         "success": false,
         *         "error": {
         *             "body": "Zły token",
         *             "description": "Token jest pusty?"
         *         }
         *     }
         *
         */
        routes.get("/api/checkToken/{token}", new CheckToken());

        /**
         * @api {get} api/karainfo/:token/:id Informacje o karze
         * @apiName karainfo
         * @apiDescription Wyświetla informacje o karze poprzez ID
         * @apiGroup Kary
         * @apiVersion 1.0.0
         * @apiParam {Number} id ID Kary
         * @apiParam {String} token Token
         *
         * @apiSuccess {String} success Czy zapytanie się udało
         * @apiSuccess {Kara} data Odpowiedź w postaci kary
         * @apiSuccess {String} id ID kary
         * @apiSuccess {Object} kara Kara
         * @apiSuccess {Number} kara.karaId ID kary
         * @apiSuccess {String} kara.karanyId Karany użytkownik
         * @apiSuccess {String} kara.mcNick Nick, który gracz miał ustawiony, gdy dostawał karę
         * @apiSuccess {String} kara.admId Nick administratora
         * @apiSuccess {Number} kara.timestamp Czas nadania kary
         * @apiSuccess {String} kara.typKary Typ kary (KICK, BAN, MUTE, TEMPBAN, TEMPMUTE, UNMUTE, UNBAN)
         * @apiSuccess {Boolean} kara.aktywna Czy kara jest aktywna
         * @apiSuccess {String} kara.messageUrl Link do wiadomości, która została napisana na kanale #logi
         * @apiSuccess {Boolean} kara.punAktywna Czy aktywna jako punish (raczej bez użycia to jest)
         * @apiSuccess {Number} kara.end Czas zakończenie kary (tylko przy karze TEMPBAN, TEMPMUTE)
         * @apiSuccess {Number} kara.duration Na jaki czas nadano
         *
         * @apiError {Boolean} success Czy zapytanie się udało
         * @apiError {Object} error Odpowiedź
         * @apiError {Boolean} error.body Krótka odpowiedź błędu
         * @apiError {Boolean} error.description Długa odpowiedź błędu
         *
         * @apiSuccessExample {json}
         *     HTTP/1.1 200 OK
         *     {
         *        "success": true,
         *        "data": {
         *           "id": "678",
         *           "kara": {
         *               "karaId": 678,
         *               "karanyId": "[VIP] gracz123 (lub gracz#1234 jeżeli nie ma go na serwerze)"",
         *               "mcNick": "gracz123",
         *               "admId": "[POM] KAMIL0024 (lub KAMIL0024#1234 jeżeli nie ma go na serwerze)",
         *               "powod": "Powód",
         *               "timestamp": 1595685472444,
         *               "typKary": "TEMPBAN",
         *               "aktywna": true,"
         *               "messageUrl": "422016694408577025/533703342195605523/736583005367435294",
         *               "end": 1595696272444,
         *               "duration": "3h",
         *               "punAktywna": true
         *           }
         *        }
         *     }
         *
         *
         * @apiErrorExample {json}
         *     HTTP/1.1 200 OK
         *     {
         *         "success": false,
         *         "error": {
         *             "body": "Złe ID",
         *             "description": "ID kary jest puste lub nie jest liczbą"
         *          }
         *      }
         *
         */
        routes.get("/api/karainfo/{token}/{id}", new Karainfo(caseDao, this));

        /**
         * @api {get} api/listakar/:token/:nick Informacje o karach
         * @apiName listakar
         * @apiDescription Wyświetla informacje o karze poprzez nick użytkownika
         * @apiGroup Kary
         * @apiVersion 1.0.0
         * @apiParam {String} nick Nick gracza
         * @apiParam {Token} token Token
         *
         * @apiSuccess {String} success Czy zapytanie się udało
         * @apiSuccess {Kara} data Odpowiedź w postaci kary
         * @apiSuccess {String} id ID kary
         * @apiSuccess {Kara} data Data w postaci kary
         * @apiSuccess {Number} kara.karaId ID kary
         * @apiSuccess {String} kara.karanyId Karany użytkownik
         * @apiSuccess {String} kara.mcNick Nick, który gracz miał ustawiony, gdy dostawał karę
         * @apiSuccess {String} kara.admId Nick administratora
         * @apiSuccess {Number} kara.timestamp Czas nadania kary
         * @apiSuccess {String} kara.typKary Typ kary (KICK, BAN, MUTE, TEMPBAN, TEMPMUTE, UNMUTE, UNBAN)
         * @apiSuccess {Boolean} kara.aktywna Czy kara jest aktywna
         * @apiSuccess {String} kara.messageUrl Link do wiadomości, która została napisana na kanale #logi
         * @apiSuccess {Boolean} kara.punAktywna Czy aktywna jako punish (raczej bez użycia to jest)
         * @apiSuccess {Number} kara.end Czas zakończenie kary (tylko przy karze TEMPBAN, TEMPMUTE)
         * @apiSuccess {Number} kara.duration Na jaki czas nadano
         *
         * @apiError {Boolean} success Czy zapytanie się udało
         * @apiError {Object} error Odpowiedź
         * @apiError {Boolean} error.body Krótka odpowiedź błędu
         * @apiError {Boolean} error.description Długa odpowiedź błędu
         * @apiSuccessExample {json}
         *     HTTP/1.1 200 OK
         *     {
         *         "success": true,
         *         "data": {
         *             "id": "600",
         *             "kara": {
         *                 "karaId": 600,
         *                 "karanyId": "[VIP] gracz123 (lub gracz#1234 jeżeli nie ma go na serwerze)",
         *                 "mcNick": "gracz123",
         *                 "admId": "[POM] KAMIL0024 (lub KAMIL0024#1234 jeżeli nie ma go na serwerze)",
         *                 "powod": "Omijanie bana",
         *                 "timestamp": 1595536961248,
         *                 "typKary": "BAN",
         *                 "aktywna": true,
         *                 "messageUrl": "https://discordapp.com/channels/1234/1234/1234",
         *                 "punAktywna": false
         *             }
         *         }
         *     }
         *
         * @apiSuccessExample {json}
         *     HTTP/1.1 200 OK
         *     {
         *          "success": true,
         *          "data": [
         *             {
         *                  "id": "680",
         *                  "kara": {
         *                       "karaId": 680,
         *                       "karanyId": "niezesrajsie_",
         *                       "mcNick": "niezesrajsie_",
         *                       "admId": "[POM] matc2002",
         *                       "powod": "Nadmierny spam",
         *                       "timestamp": 1595685781024,
         *                       "typKary": "TEMPMUTE",
         *                       "aktywna": false,
         *                       "messageUrl":"https://discordapp.com/channels/422016694408577025/533703342195605523/736584298823417909",
         *                       "end": 1595689381024,
         *                       "duration": "1h",
         *                       "punAktywna": true
         *                 }
         *             },
         *             {
         *                  "id": "680",
         *                  "kara": {
         *                       "karaId": 680,
         *                       "karanyId": "niezesrajsie_",
         *                       "mcNick": "niezesrajsie_",
         *                       "admId": "[POM] matc2002",
         *                       "powod": "Nadmierny spam",
         *                       "timestamp": 1595685781024,
         *                       "typKary": "TEMPMUTE",
         *                       "aktywna": false,
         *                       "messageUrl":"https://discordapp.com/channels/422016694408577025/533703342195605523/736584298823417909",
         *                       "end": 1595689381024,
         *                       "duration": "1h",
         *                       "punAktywna": true
         *                 }
         *             }
         *         ]
         *     }
         *
         *
         * @apiErrorExample {json}
         *     HTTP/1.1 200 OK
         *     {
         *         "success": false,
         *         "error": {
         *             "body": "Zły nick",
         *             "description": "Ten nick nie ma żadnej kary"
         *          }
         *      }
         *
         */
        routes.get("/api/listakar/{token}/{nick}", new Listakar(caseDao, this));

        this.server = Undertow.builder()
                .addHttpListener(1234, "0.0.0.0")
                .setHandler(path()
                        .addPrefixPath("/", wrapWithMiddleware(routes)))
                .build();
        this.server.start();

        return true;
    }

    @Override
    public boolean shutDown() {
        this.server.stop();
        return true;
    }

    @Override
    public String getName() {
        return "API";
    }

    @Override
    public boolean isStart() {
        return this.start;
    }

    @Override
    public void setStart(boolean bol) {
        this.start = bol;
    }

    private static HttpHandler wrapWithMiddleware(HttpHandler handler) {
        return MiddlewareBuilder.begin(BlockingHandler::new).complete(handler);
    }

    // Cache

    public UserinfoConfig getUserConfig(String id) {
        return ucCache.get(id, this::get);
    }

    private UserinfoConfig get(String id) {
        UserinfoConfig uc = new UserinfoConfig(id);
        User u = api.retrieveUserById(id).complete();
        Member mem = guild.retrieveMember(u).complete();

        if (mem != null) {
            if (mem.getNickname() != null) uc.setMcNick(mem.getNickname());
        }

        uc.setFullname(UserUtil.getName(u));

        ucCache.put(id, uc);
        return uc;
    }

}
