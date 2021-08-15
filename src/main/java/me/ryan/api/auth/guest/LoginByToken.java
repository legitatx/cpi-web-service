package me.legit.api.auth.guest;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import me.legit.APICore;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LoginByToken implements Handler {

    private static OkHttpClient client = new OkHttpClient.Builder().build();

    public LoginByToken() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String token = ctx.header("Authorization").split("Bearer ")[1];

        JSONObject object = new JSONObject();
        object.put("token", token);
        object.put("returnSecureToken", true);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), object.toString());

        String apiKey = "AIzaSyAEbxc1jwhD7tKL4V_oeDsTCH0Ees3DIng";
        Request fetchLoginToken = new Request.Builder()
                .url("https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyCustomToken?key=" + apiKey)
                .post(body)
                .build();

        ctx.contentType("application/json");

        CompletableFuture<String> resultFuture = new CompletableFuture<>();

        client.newCall(fetchLoginToken).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                APICore.getLogger().severe("An error occurred while attempting to fetch login token for a user! - " + "Call: " + call.request().toString());
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
                        APICore.getLogger().severe("An error occurred while attempting to fetch login token for a user! - " + "Code: " + response.code() + " - Body: " + ltrBody);

                        JsonParser parser = new JsonParser();
                        JsonElement element = parser.parse(ltrBody);

                        JsonObject errorObject = element.getAsJsonObject().getAsJsonObject("error");
                        if (errorObject != null) {
                            Sentry.getContext().recordBreadcrumb(
                                    new BreadcrumbBuilder()
                                            .withData("code", String.valueOf(response.code()))
                                            .withData("body", ltrBody)
                                            .withData("errorObj", errorObject.toString())
                                            .build()
                            );

                            int code = errorObject.get("code").getAsInt();

                            Sentry.capture("An error occurred while attempting to fetch login token for a user!");

                            ctx.status(code);
                            resultFuture.complete(returnErrorObject(errorObject).toString());
                        } else {
                            Sentry.getContext().recordBreadcrumb(
                                    new BreadcrumbBuilder()
                                            .withData("code", String.valueOf(response.code()))
                                            .withData("body", ltrBody)
                                            .withData("errorObj", "Unknown")
                                            .build()
                            );

                            Sentry.capture("An error occurred while attempting to fetch login token for a user!");

                            ctx.status(response.code());
                            resultFuture.complete(new JSONObject().put("status", response.code()).put("message", "An unknown error occurred while attempting to fetch a login token.").toString());
                        }
                    } else {
                        APICore.getLogger().info(ltrBody);

                        JsonParser parser = new JsonParser();
                        JsonElement element = parser.parse(ltrBody);
                        JsonObject tokenObject = element.getAsJsonObject();

                        String accessToken = tokenObject.get("idToken").getAsString();
                        String refreshToken = tokenObject.get("refreshToken").getAsString();

                        try {
                            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken).get();
                            APICore.getLogger().info("Successfully decoded and verified login token for user: " + decodedToken.getUid());

                            JSONObject success = new JSONObject();
                            success.put("success", true);
                            success.put("access_token", accessToken);
                            success.put("refresh_token", refreshToken);
                            success.put("uid", decodedToken.getUid());

                            resultFuture.complete(success.toString());
                        } catch (ExecutionException e) {
                            if (e.getCause() instanceof FirebaseAuthException) {
                                FirebaseAuthException authError = (FirebaseAuthException) e.getCause();

                                //TODO properly implement this #2
                                JSONObject authErrorObj = new JSONObject();
                                authErrorObj.put("error", 500);
                                authErrorObj.put("type", authError.getErrorCode());
                                authErrorObj.put("message", "An error occurred while attempting to verify login token.");

                                e.printStackTrace();
                                Sentry.getContext().addExtra("halted", true);
                                Sentry.getContext().addExtra("errorObj", authErrorObj.toString());
                                Sentry.capture(e);

                                ctx.status(500);
                                resultFuture.complete(authErrorObj.toString());
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
    }

    private static JSONObject returnErrorObject(JsonObject object) {
        int code = object.get("code").getAsInt();
        String message = object.get("message").getAsString();

        JSONObject errorObject = new JSONObject();
        errorObject.put("error", code);
        errorObject.put("type", message);
        errorObject.put("message", "An error occurred while attempting to login by token.");
        return errorObject;
    }
}
