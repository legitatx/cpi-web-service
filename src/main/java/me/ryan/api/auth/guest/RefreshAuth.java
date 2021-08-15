package me.legit.api.auth.guest;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import me.legit.APICore;
import me.legit.punishment.PunishmentData;
import me.legit.punishment.PunishmentHistory;
import me.legit.punishment.PunishmentType;
import me.legit.utils.FirebaseError;
import me.legit.utils.Utilities;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static me.legit.utils.Utilities.generateError;

public class RefreshAuth implements Handler {

    private static OkHttpClient client = new OkHttpClient.Builder().build();

    public RefreshAuth() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String refreshToken = ctx.pathParam("token");

        JSONObject object = new JSONObject();
        object.put("grant_type", "refresh_token");
        object.put("refresh_token", refreshToken);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), object.toString());

        String apiKey = "AIzaSyAEbxc1jwhD7tKL4V_oeDsTCH0Ees3DIng";
        Request fetchAccessToken = new Request.Builder()
                .url("https://securetoken.googleapis.com/v1/token?key=" + apiKey)
                .post(body)
                .build();

        ctx.contentType("application/json");

        CompletableFuture<String> resultFuture = new CompletableFuture<>();

        client.newCall(fetchAccessToken).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                APICore.getLogger().severe("An error occurred while attempting to fetch a new access token for a user! - " + "Call: " + call.request().toString());
                Sentry.getContext().recordBreadcrumb(
                        new BreadcrumbBuilder()
                                .withData("clientCall", call.request().toString())
                                .build()
                );
                e.printStackTrace();
                Sentry.capture(e);

                ctx.status(500);
                resultFuture.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String ltrBody = responseBody.string();
                    if (!response.isSuccessful()) {
                        APICore.getLogger().severe("An error occurred while attempting to fetch a new access token for a user! - " + "Code: " + response.code() + " - Body: " + ltrBody);

                        JsonParser parser = new JsonParser();
                        JsonElement element = parser.parse(ltrBody);

                        JsonObject errorObject = element.getAsJsonObject().getAsJsonObject("error");
                        if (errorObject != null) {
                            int code = errorObject.get("code").getAsInt();
                            String message = errorObject.get("message").getAsString();
                            FirebaseError error = FirebaseError.valueOf(message);

                            JSONObject errorObj;
                            switch (error) {
                                case TOKEN_EXPIRED:
                                    errorObj = generateError("AUTHORIZATION_INVALID_OR_EXPIRED_TOKEN", "null", FirebaseError.TOKEN_EXPIRED.getMessage());
                                    break;
                                case USER_DISABLED:
                                    errorObj = generateError("GUEST_GATED_LOCATION", "null", FirebaseError.USER_DISABLED.getMessage());
                                    break;
                                case USER_NOT_FOUND:
                                    errorObj = generateError("AUTHORIZATION_CREDENTIALS", "null", FirebaseError.USER_NOT_FOUND.getMessage());
                                    break;
                                case INVALID_REFRESH_TOKEN:
                                    errorObj = generateError("AUTHORIZATION_INVALID_REFRESH_TOKEN", "null", FirebaseError.INVALID_REFRESH_TOKEN.getMessage());
                                    break;
                                default:
                                    errorObj = generateError("AUTHORIZATION_CREDENTIALS", "null", "An unknown error occurred while attempting to fetch a new access token.");
                                    break;
                            }

                            Sentry.getContext().recordBreadcrumb(
                                    new BreadcrumbBuilder()
                                            .withData("code", String.valueOf(response.code()))
                                            .withData("body", ltrBody)
                                            .withData("errorObj", errorObj.toString())
                                            .build()
                            );

                            Sentry.capture("An error occurred while attempting to fetch a new access token for a user!");

                            ctx.status(code);
                            resultFuture.complete(errorObj.toString());
                        } else {
                            Sentry.getContext().recordBreadcrumb(
                                    new BreadcrumbBuilder()
                                            .withData("code", String.valueOf(response.code()))
                                            .withData("body", ltrBody)
                                            .withData("errorObj", "Unknown")
                                            .build()
                            );

                            Sentry.capture("An error occurred while attempting to fetch a new access token for a user!");

                            ctx.status(response.code());
                            resultFuture.complete(generateError("AUTHORIZATION_CREDENTIALS", "null", "An unknown error occurred while attempting to fetch a new access token.").toString());
                        }
                    } else {
                        APICore.getLogger().info(ltrBody);

                        JsonParser parser = new JsonParser();
                        JsonElement element = parser.parse(ltrBody);
                        JsonObject tokenObject = element.getAsJsonObject();

                        String uid = tokenObject.get("user_id").getAsString();

                        PunishmentHistory history = APICore.getPunishmentManager().getPunishmentHistory(uid);
                        if (history != null) {
                            JSONObject responseObj;
                            if (history.getTotalBans() >= 3) {
                                responseObj = generateError("PROFILE_DISABLED", "null", "This account is permanently banned.");
                                resultFuture.complete(responseObj.toString());
                                return;
                            } else if (history.getTotalBans() < 3) {
                                List<PunishmentData> punishments = history.getPunishments();
                                if (punishments != null && punishments.size() > 0) {
                                    for (PunishmentData data : punishments) {
                                        if (data.getType().equals(PunishmentType.BAN)) {
                                            LocalDateTime nowTime = LocalDateTime.now(ZoneOffset.UTC);
                                            LocalDateTime expireTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(data.getExpiresIn()), ZoneOffset.UTC);
                                            if (nowTime.isBefore(expireTime)) {
                                                responseObj = generateError("PROFILE_USER_BANNED", "null", "This account is banned until " + expireTime.toString() + ".");
                                                resultFuture.complete(responseObj.toString());
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        resultFuture.complete(generateTokenResponse(tokenObject).toString());
                    }
                }
            }
        });

        ctx.result(resultFuture);
    }

    private JsonObject generateTokenResponse(JsonObject tokenObject) {
        JsonObject finalResponse = new JsonObject();

        JsonObject data = new JsonObject();
        data.add("etag", JsonNull.INSTANCE);
        JsonObject token = new JsonObject();
        String accessToken = tokenObject.get("access_token").getAsString();
        token.addProperty("access_token", accessToken);
        token.addProperty("refresh_token", tokenObject.get("refresh_token").getAsString());
        token.addProperty("swid", tokenObject.get("user_id").getAsString());
        token.addProperty("ttl", Integer.valueOf(tokenObject.get("expires_in").getAsString()));
        token.addProperty("refresh_ttl", 15552000);
        token.addProperty("high_trust_expires_in", 561);
        token.addProperty("initial_grant_in_chain_time", ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli());
        token.addProperty("scope", "AUTHZ_GUEST_SECURED_SESSION disneyid-guest-hallpass disneyid-profile-b2b-read disneyid-profile-guest-read disneyid-profile-guest-update");
        token.add("sso", JsonNull.INSTANCE);
        token.add("blue_cookie", JsonNull.INSTANCE);
        token.add("id_token", JsonNull.INSTANCE);
        token.addProperty("authenticator", "disneyid");
        token.add("loginValue", JsonNull.INSTANCE);
        token.add("clickbackType", JsonNull.INSTANCE);
        token.addProperty("sessionTransferKey", Base64.getEncoder().encodeToString(Utilities.getSignatureGenerator().hashString(accessToken, StandardCharsets.UTF_8).asBytes()));
        data.add("token", token);

        finalResponse.add("data", data);
        finalResponse.add("error", JsonNull.INSTANCE);

        return finalResponse;
    }
}
