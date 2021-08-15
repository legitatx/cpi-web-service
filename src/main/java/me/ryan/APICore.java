package me.legit;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.plugin.json.JavalinJson;
import io.sentry.Sentry;
import me.legit.api.auth.guest.*;
import me.legit.api.auth.mix.*;
import me.legit.api.auth.mix.friendships.MixFriendship;
import me.legit.api.auth.mix.friendships.MixFriendshipDelete;
import me.legit.api.auth.mix.friendships.MixFriendshipInvitation;
import me.legit.api.auth.mix.friendships.MixFriendshipInvitationDelete;
import me.legit.api.game.breadcrumb.Breadcrumb;
import me.legit.api.game.breadcrumb.RemoveBreadcrumbs;
import me.legit.api.game.catalog.Stats;
import me.legit.api.game.chat.Verify;
import me.legit.api.game.consumable.Use;
import me.legit.api.game.encryption.EncryptionKey;
import me.legit.api.game.game.Igloo;
import me.legit.api.game.game.Rooms;
import me.legit.api.game.game.Worlds;
import me.legit.api.game.igloo.Popular;
import me.legit.api.game.igloo.PopulationByZoneIds;
import me.legit.api.game.igloo.UpdateData;
import me.legit.api.game.igloo.decoration.DeleteDecoration;
import me.legit.api.game.igloo.decoration.GetDecoration;
import me.legit.api.game.igloo.decoration.PurchaseDecoration;
import me.legit.api.game.igloo.layout.*;
import me.legit.api.game.inventory.CreateCustomEquipment;
import me.legit.api.game.inventory.DeleteCustomEquipment;
import me.legit.api.game.inventory.GetInventory;
import me.legit.api.game.minigame.FishingCast;
import me.legit.api.game.minigame.FishingCatch;
import me.legit.api.game.player.*;
import me.legit.api.game.player.data.*;
import me.legit.api.game.quest.Progress;
import me.legit.api.game.quest.SetStatus;
import me.legit.api.game.reward.*;
import me.legit.api.game.store.Purchase;
import me.legit.api.game.tube.Tube;
import me.legit.api.game.tutorial.Tutorial;
import me.legit.database.DatabaseManager;
import me.legit.models.quest.QuestManager;
import me.legit.models.reward.RewardManager;
import me.legit.punishment.PunishmentManager;
import me.legit.utils.ChatFilter;
import me.legit.utils.RSAHelper;
import me.legit.service.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static io.javalin.apibuilder.ApiBuilder.*;

public class APICore {

    private static final Logger logger = Logger.getLogger(APICore.class.getName());

    private static final DatabaseManager databaseManager = new DatabaseManager();

    private static ChatFilter chatFilter;
    private static RSAHelper rsaHelper;
    private static RewardManager rewardManager;
    private static QuestManager questManager;
    private static PunishmentManager punishmentManager;
    private static ProgressionService progressionService;
    private static TemplateService templateService;
    private static ClaimableRewardService claimableRewardService;
    private static PickupService pickupService;
    private static DailySpinService dailySpinService;
    private static MinigameService minigameService;
    private static DisneyStoreService disneyStoreService;
    private static PropsService propsService;
    private static DecorationService decorationService;

    public static void main(String[] args) {
        Security.setProperty("crypto.policy", "unlimited");

        Sentry.init();

        try {
            rsaHelper = new RSAHelper();
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            APICore.getLogger().severe("An error occurred while attempting to initialize RSAHelper class!");
            e.printStackTrace();
        }

        punishmentManager = new PunishmentManager(databaseManager.firebase().getFirestore());
        chatFilter = new ChatFilter(databaseManager.firebase().getFirestore());
        rewardManager = new RewardManager(databaseManager.firebase().getFirestore());
        questManager = new QuestManager();
        progressionService = new ProgressionService();
        templateService = new TemplateService();
        claimableRewardService = new ClaimableRewardService();
        pickupService = new PickupService();
        dailySpinService = new DailySpinService();
        minigameService = new MinigameService();
        disneyStoreService = new DisneyStoreService();
        propsService = new PropsService();
        decorationService = new DecorationService();

        Gson gson = new GsonBuilder().create();
        JavalinJson.setFromJsonMapper(gson::fromJson);
        JavalinJson.setToJsonMapper(gson::toJson);

        Javalin.create(config -> {
            //config.enableDevLogging()
            config.requestLogger((ctx, timeMs) -> APICore.getLogger().info(ctx.method() + " " + ctx.path() + " took " + timeMs + " ms"));
            config.addSinglePageRoot("/", "index.html");
        }).routes(() -> {
            // Auth - Guest APIs
            path("/jgc/v5/client/*", () -> {
                get("/configuration/site", new SiteConfiguration());
                post("/guest/register", new Register());
                post("/guest/login", new LoginByPassword());
                post("/guest/refresh-auth/:token", new RefreshAuth());
                post("/validate", new Validate());
                post("/api-key", new APIKey());
            });

            // Auth - Mix APIs
            path("/mix", () -> {
                post("/state", new MixState());
                post("/languagePreference", new MixLanguagePreference());
                post("/search/displayname", new MixSearchName());
                post("/displayname/validate/v2", new MixValidateName());
                put("/displayname", new MixDisplayName());

                path("/session", () -> {
                    put("/user", new MixSession());
                    post("/user/delete", new MixSessionDelete());
                });

                put("/friendship", new MixFriendship());
                post("/friendship/delete", new MixFriendshipDelete());
                put("/friendship/invitation", new MixFriendshipInvitation());
                post("/friendship/invitation/delete", new MixFriendshipInvitationDelete());

                path("/moderation", () -> put("/text", new MixModerationText()));
                path("/registration", () -> post("/text", new MixRegistrationText()));
                path("/notifications", () -> post("/sinceSequence", new MixNotificationsSinceSequence()));
            });

            // Auth - Custom APIs
            path("/auth", () -> path("/v1", () -> {
                path("/user", () -> {
                    path("/login", () -> {
                        post("/token", new LoginByToken());
                    });
                });
            }));

            // Player APIs
            path("/player", () -> {
                get("/v1/", new Player());
                post("/v1/profile", new Profile());
                put("/v1/referral", new Referral());
                get("/v1/durable/equip/:id", new Durable());
                post("/v1/outfit", new Outfit());

                post("/v1/id/online", new GetOnlinePlayersBySwids());
                get("/v1/name/:name", new GetOtherPlayerDataByDisplayName());
                post("/v1/names", new GetOtherPlayerDataByDisplayNames());
                get("/v1/id/:swid", new GetOtherPlayerDataBySwid());
                post("/v1/id", new GetOtherPlayerDataBySwids());
            });

            // Game APIs
            path("/game", () -> path("/v1", () -> {
                get("/rooms", new Rooms());
                post("/worlds/CPI/language/:language/rooms/:room/players", new Worlds());
                post("/igloos/language/:language/players", new Igloo());
            }));

            // Reward APIs
            path("/reward", () -> path("/v1", () -> {
                put("/", new Add());
                get("/preregistration", new Preregistration());
                post("/claimServerAdded", new ClaimServerAdded());
                put("/claim/:id", new Claim());
                post("/room", new Room());
                post("/claimDailySpin", new ClaimDailySpin());
                post("/claimQuickNotification", new ClaimQuickNotification());
                get("/calculateexchangeall", new CalculateExchangeAll());
                post("/exchangeall", new ExchangeAll());
            }));

            // Quest APIs
            path("/quest", () -> path("/v1", () -> {
                // Nasty route setup because setting a named parameter conflicts with /progress unfortunately
                // Haven't found a way to bypass that yet, will look into it later

                SetStatus setStatus = new SetStatus();
                post("/AAC001Q001LeakyShip", setStatus);
                post("/AAC001Q002Lava", setStatus);
                post("/AAC001Q003Hatch", setStatus);
                post("/AAC001Q004Crabs", setStatus);
                post("/AAC001Q005Lighthouse", setStatus);
                post("/AAC002Q001Machine", setStatus);
                post("/AAC002Q002View", setStatus);
                post("/AAC002Q003Dots", setStatus);
                post("/AAC002Q004Icy", setStatus);
                post("/AAC002Q005Storm", setStatus);
                post("/AAC002Q006Stolen", setStatus);
                post("/AAC002Q007Click", setStatus);
                post("/AAC002Q008Defender", setStatus);
                post("/AAC002Q009Treasure", setStatus);
                post("/AAC002Q010Skyberg", setStatus);
                post("/DJC001Q001Plan", setStatus);
                post("/DJC001Q002Stars", setStatus);
                post("/DJC001Q003Meeting", setStatus);
                post("/DJC001Q004Tea", setStatus);
                post("/DJC001Q005Concert", setStatus);
                post("/RHC001Q001TreasureQuest", setStatus);
                post("/RHC001Q002SwabTheDeck", setStatus);
                post("/RHC001Q003CursedDummy", setStatus);
                post("/RHC001Q004ShellRiddles", setStatus);
                post("/RHC001Q005CureTheCurse", setStatus);
                post("/RHC001Q006CursedTrail", setStatus);
                post("/RHC001Q007NavigatorsPuzzle", setStatus);
                post("/RHC001Q008GoodImpressions", setStatus);
                post("/RHC001Q009BlackPearl", setStatus);
                post("/RHC001Q010CaptainsShare", setStatus);
                post("/RKC001Q001Drop", setStatus);
                post("/RKC001Q002Fix", setStatus);
                post("/RKC001Q003Tube", setStatus);
                post("/RKC001Q004Safety", setStatus);
                post("/RKC001Q005Delivery", setStatus);
                post("/RKC002Q001Peak", setStatus);
                post("/RKC002Q002Detector", setStatus);
                post("/RKC002Q003Windy", setStatus);
                post("/RKC002Q004MakeGood", setStatus);
                post("/RKC002Q005Colder", setStatus);

                post("/progress", new Progress());
            }));

            // Inventory APIs
            path("/inventory", () -> path("/v1", () -> {
                get("/equipment", new GetInventory());
                post("/equipment", new CreateCustomEquipment());
                delete("/equipment/:id", new DeleteCustomEquipment());
            }));

            // Tutorial APIs
            path("/tutorial", () -> path("/v1", () -> {
                post("/tutorial", new Tutorial());
            }));

            // Breadcrumb APIs
            path("/breadcrumb", () -> path("/v1", () -> {
                post("/breadcrumb", new Breadcrumb());
                post("/removebreadcrumbs", new RemoveBreadcrumbs());
            }));

            // Catalog APIs
            path("/catalog", () -> path("/v1", () -> {
                get("/clothing/themes/stats", new Stats());
            }));

            // Encryption APIs
            path("/encryption-trusted", () -> path("/v1", () -> {
                post("/encryptionKey", new EncryptionKey());
            }));

            // Chat APIs
            path("/chat", () -> path("/v1", () -> {
                post("/message/verify", new Verify());
            }));

            // Tube APIs
            path("/tube", () -> path("/v1", () -> {
                put("/equip/:id", new Tube());
            }));

            // Minigame APIs
            path("/minigame", () -> path("/v1", () -> {
                post("/fishing/cast", new FishingCast());
                post("/fishing/catch", new FishingCatch());
            }));

            // Disney Store APIs
            path("/disneystore", () -> path("/v1", () -> {
                put("/purchase/:type/:count", new Purchase());
            }));

            // Consumable APIs
            path("/consumable", () -> {
                get("/v1/", new me.legit.api.game.consumable.GetInventory());
                put("/v1/:type/:count", new me.legit.api.game.consumable.Purchase());
                post("/v1/partial", new me.legit.api.game.consumable.StorePartial());
                delete("/v1/:type", new Use());
            });

            // Igloo APIs
            path("/igloo", () -> path("/v1", () -> {
                path("/decorations", () -> {
                    get("/", new GetDecoration());
                    delete("/:id", new DeleteDecoration());
                    post("/:id/increment/:count", new PurchaseDecoration());
                });

                path("/layout", () -> {
                    post("/", new CreateLayout());
                    delete("/:id", new DeleteLayout());
                    get("/:id", new GetLayout());
                    put("/:id", new UpdateLayout());
                });

                put("/", new UpdateData());
                post("/iglooId/layout/active", new GetActiveLayout());
                post("/igloos/populations/zoneIds", new PopulationByZoneIds());
                get("/igloos/popular", new Popular());
            }));
        }).exception(ExecutionException.class, (e, ctx) -> {
            if (e.getCause() instanceof FirebaseAuthException) {
                FirebaseAuthException authError = (FirebaseAuthException) e.getCause();
                    if (ctx.path().contains("mix")) {
                        APICore.getLogger().severe("A FirebaseAuthException was thrown while attempting to generate a response from a Mix endpoint! (URL: " + ctx.path() + ")");
                        e.printStackTrace();

                        Sentry.getContext().addExtra("errorCode", authError.getErrorCode());
                        Sentry.getContext().addTag("message", "A FirebaseAuthException was thrown while attempting to generate a response from a Mix endpoint! (URL: " + ctx.path() + ")");
                        Sentry.capture(e);

                        ctx.status(401);
                        ctx.result(new JSONObject().put("Status", "UNAUTHORIZED_ONEID_TOKEN").toString());
                    } else {
                        APICore.getLogger().severe("A FirebaseAuthException was thrown while attempting to generate a response from a game endpoint! (URL: " + ctx.path() + ")");
                        e.printStackTrace();

                        Sentry.getContext().addExtra("errorCode", authError.getErrorCode());
                        Sentry.getContext().addTag("message", "A FirebaseAuthException was thrown while attempting to generate a response from a game endpoint! (URL: " + ctx.path() + ")");
                        Sentry.capture(e);

                        ctx.status(401);
                        ctx.result("{\"message\":\"Failed to authenticate the command call with any providers configured.\",\"code\":3}");
                    }
            } else {
                APICore.getLogger().severe("An ExecutionException was thrown while attempting to generate a response from an endpoint! (URL: " + ctx.path() + ")");
                e.printStackTrace();

                Sentry.getContext().addTag("message", "An ExecutionException was thrown while attempting to generate a response from an endpoint! (URL: " + ctx.path() + ")");
                Sentry.capture(e);

                ctx.status(500);
            }
        }).exception(InterruptedException.class, (e, ctx) -> {
            APICore.getLogger().severe("An InterruptedException was thrown while attempting to generate a response from an endpoint! Most likely failed to resolve a future! (URL: " + ctx.path() + ")");
            e.printStackTrace();

            Sentry.getContext().addTag("message", "An InterruptedException was thrown while attempting to generate a response from an endpoint! Most likely failed to resolve a future! (URL: " + ctx.path() + ")");
            Sentry.capture(e);

            ctx.status(500);
        }).exception(NumberFormatException.class, (e, ctx) -> {
            APICore.getLogger().severe("A user sent an invalid request to an endpoint, could not verify a path parameter that should be a number! (Body: " + ctx.body() + ") (URL: " + ctx.path() + ")");
            e.printStackTrace();

            Sentry.getContext().addTag("message", "A user sent an invalid request to an endpoint, could not verify a path parameter that should be a number! (Body: " + ctx.body() + ") (URL: " + ctx.path() + ")");
            Sentry.capture(e);

            ctx.status(400);
            ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
        }).exception(Exception.class, (e, ctx) -> {
            APICore.getLogger().severe("An unknown exception was thrown while attempting to generate a response from an endpoint! (URL: " + ctx.path() + ")");

            e.printStackTrace();
            Sentry.capture(e);

            ctx.status(500);
        }).error(404, ctx -> {
            ctx.result("{\"message\":\"Not found\",\"code\":404}");
        }).start(getHerokuAssignedPort());

        Runtime.getRuntime().addShutdownHook(new Thread(databaseManager::disable));

        logger.info("Started CPI API!");
    }

    private static int getHerokuAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 7000;
    }

    public static Logger getLogger() {
        return logger;
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static RSAHelper getRsaHelper() {
        return rsaHelper;
    }

    public static ChatFilter getChatFilter() {
        return chatFilter;
    }

    public static PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public static RewardManager getRewardManager() {
        return rewardManager;
    }

    public static QuestManager getQuestManager() {
        return questManager;
    }

    public static ProgressionService getProgressionService() {
        return progressionService;
    }

    public static TemplateService getTemplateService() {
        return templateService;
    }

    public static ClaimableRewardService getClaimableRewardService() {
        return claimableRewardService;
    }

    public static PickupService getPickupService() {
        return pickupService;
    }

    public static DailySpinService getDailySpinService() {
        return dailySpinService;
    }

    public static MinigameService getMinigameService() {
        return minigameService;
    }

    public static DisneyStoreService getDisneyStoreService() {
        return disneyStoreService;
    }

    public static PropsService getPropsService() {
        return propsService;
    }

    public static DecorationService getDecorationService() {
        return decorationService;
    }
}
