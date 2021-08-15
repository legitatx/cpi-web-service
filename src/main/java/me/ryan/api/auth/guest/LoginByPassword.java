package me.legit.api.auth.guest;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.punishment.PunishmentData;
import me.legit.punishment.PunishmentHistory;
import me.legit.punishment.PunishmentType;
import me.legit.utils.FirebaseError;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.legit.utils.Utilities.generateError;
import static me.legit.utils.Utilities.generateResponse;

public class LoginByPassword implements Handler {

    private static OkHttpClient client = new OkHttpClient.Builder().build();
    private JsonParser parser;

    public LoginByPassword() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        JsonObject loginObj = parser.parse(ctx.body()).getAsJsonObject();
        String username = loginObj.get("loginValue").getAsString();
        String password = loginObj.get("password").getAsString();

        ctx.contentType("application/json");

        if (username != null && password != null) {
            Sentry.getContext().setUser(new UserBuilder().setUsername(username).build());

            Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

            ApiFuture<QuerySnapshot> usernameSearch = db.collection("users").whereEqualTo("name.username", username).get();
            ApiFuture<QuerySnapshot> displayNameSearch = db.collection("users").whereEqualTo("name.displayName", username).get();
            List<QueryDocumentSnapshot> documents = Stream.concat(usernameSearch.get().getDocuments().stream(), displayNameSearch.get().getDocuments().stream()).collect(Collectors.toList());

            if (documents.size() == 0) {
                JSONObject errorObj = generateError("AUTHORIZATION_CREDENTIALS", "null", "An unknown error occurred while attempting to authenticate the user.");
                Sentry.capture("No documents found while attempting to verify password for a user!");
                ctx.status(400);
                ctx.result(errorObj.toString());
                return;
            }

            QueryDocumentSnapshot document = documents.get(0); // should never be more than 1 document returned

            ApiFuture<UserRecord> record = FirebaseAuth.getInstance().getUserAsync(document.getId());
            UserRecord user = record.get();

            JSONObject verifyReqBody = new JSONObject();
            verifyReqBody.put("email", user.getEmail());
            verifyReqBody.put("password", password);
            verifyReqBody.put("returnSecureToken", true);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), verifyReqBody.toString());

            String apiKey = "AIzaSyAEbxc1jwhD7tKL4V_oeDsTCH0Ees3DIng";
            Request verifyPasswordRequest = new Request.Builder()
                    .url("https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=" + apiKey)
                    .post(body)
                    .build();

            CompletableFuture<String> resultFuture = new CompletableFuture<>();

            client.newCall(verifyPasswordRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    APICore.getLogger().severe("An error occurred while attempting to verify password for a user! - " + "Call: " + call.request().toString());
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
                    //TODO see if throwing exception in callback comes back out to handle method body
                    try (ResponseBody responseBody = response.body()) {
                        String vprBody = responseBody.string();
                        if (!response.isSuccessful()) {
                            APICore.getLogger().severe("An error occurred while attempting to verify password for a user! - " + "Code: " + response.code() + " - Body: " + vprBody);

                            JsonParser parser = new JsonParser();
                            JsonElement element = parser.parse(vprBody);

                            JsonObject errorObject = element.getAsJsonObject().getAsJsonObject("error");
                            if (errorObject != null) {
                                int code = errorObject.get("code").getAsInt();
                                String message = errorObject.get("message").getAsString();
                                FirebaseError error = FirebaseError.valueOf(message);

                                JSONObject errorObj;
                                switch (error) {
                                    case EMAIL_NOT_FOUND:
                                        errorObj = generateError("AUTHORIZATION_CREDENTIALS", "null", FirebaseError.EMAIL_NOT_FOUND.getMessage());
                                        break;
                                    case INVALID_PASSWORD:
                                        errorObj = generateError("AUTHORIZATION_CREDENTIALS", "null", FirebaseError.INVALID_PASSWORD.getMessage());
                                        break;
                                    case USER_DISABLED:
                                        errorObj = generateError("GUEST_GATED_LOCATION", "null", FirebaseError.USER_DISABLED.getMessage());
                                        break;
                                    default:
                                        errorObj = generateError("AUTHORIZATION_CREDENTIALS", "null", "An unknown error occurred while attempting to authenticate the user.");
                                        break;
                                }

                                Sentry.getContext().recordBreadcrumb(
                                        new BreadcrumbBuilder()
                                                .withData("code", String.valueOf(response.code()))
                                                .withData("body", vprBody)
                                                .withData("errorObj", errorObj.toString())
                                                .build()
                                );

                                Sentry.capture("An error occurred while attempting to verify password for a user!");

                                ctx.status(code);
                                resultFuture.complete(errorObj.toString());
                            } else {
                                Sentry.getContext().recordBreadcrumb(
                                        new BreadcrumbBuilder()
                                                .withData("code", String.valueOf(response.code()))
                                                .withData("body", vprBody)
                                                .withData("errorObj", "Unknown")
                                                .build()
                                );

                                Sentry.capture("An error occurred while attempting to verify password for a user!");

                                ctx.status(response.code());
                                resultFuture.complete(generateError("AUTHORIZATION_CREDENTIALS", "null", "An unknown error occurred while attempting to authenticate the user.").toString());
                            }
                        } else {
                            APICore.getLogger().info(response.toString());

                            JsonParser parser = new JsonParser();
                            JsonElement element = parser.parse(vprBody);
                            JsonObject tokenObject = element.getAsJsonObject();

                            String idToken = tokenObject.get("idToken").getAsString();
                            String refreshToken = tokenObject.get("refreshToken").getAsString();

                            try {
                                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(idToken).get();
                                APICore.getLogger().info("Successfully verified password and generated ID token for user: " + decodedToken.getUid());

                                PunishmentHistory history = APICore.getPunishmentManager().getPunishmentHistory(decodedToken.getUid());
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

                                JSONObject responseObj = generateResponse(decodedToken, idToken, refreshToken);
                                JSONObject displayName = new JSONObject((Map<String, Object>) document.get("name"));
                                displayName.remove("username");
                                responseObj.getJSONObject("data").put("displayName", displayName);

                                resultFuture.complete(responseObj.toString());
                            } catch (ExecutionException e) {
                                if (e.getCause() instanceof FirebaseAuthException) {
                                    FirebaseAuthException authError = (FirebaseAuthException) e.getCause();

                                    //TODO properly implement this
                                    APICore.getLogger().severe("A FirebaseAuthException was thrown while attempting to verify ID token for a user! (Type: " + authError.getErrorCode() + ")");
                                    e.printStackTrace();

                                    Sentry.getContext().addExtra("halted", true);
                                    Sentry.getContext().addExtra("errorCode", authError.getErrorCode());
                                    Sentry.capture(e);

                                    ctx.status(500);
                                    resultFuture.complete(generateError("AUTHORIZATION_CREDENTIALS", "null", "An unknown error occurred while attempting to authenticate the user.").toString());
                                }
                            } catch (InterruptedException ex) {
                                APICore.getLogger().severe("An error was thrown while attempting to verify ID token for a user (failed to resolve future)!");

                                ex.printStackTrace();
                                Sentry.capture(ex);

                                ctx.status(500);

                                resultFuture.completeExceptionally(ex);
                            }
                        }
                    }
                }
            });

            ctx.result(resultFuture);
        } else {
            JSONObject error = generateError("MISSING_VALUE", username == null ? "profile.username" : "password", "A username or password field is missing.");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("A username or password field was missing upon logging in a user by password!");

            ctx.status(400);
            ctx.result(error.toString());
        }
    }
}
