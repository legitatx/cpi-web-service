package me.legit.api.auth.guest;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.ChatFilter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.legit.utils.Utilities.generateError;

public class Validate implements Handler {

    private List<Pattern> patterns;
    private Zxcvbn zxcvbn;

    public Validate() {
        this.patterns = new ArrayList<>();
        this.zxcvbn = new Zxcvbn();
        patterns.add(Pattern.compile("^\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})$"));
        patterns.add(Pattern.compile("^\\+(?:[0-9] ?){6,14}[0-9]$"));
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json;charset=utf-8");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        JsonElement usernameElement = object.get("username");
        JsonElement passwordElement = object.get("password");

        if (!usernameElement.isJsonNull() && !passwordElement.isJsonNull()) {
            String username = usernameElement.getAsString();
            String password = passwordElement.getAsString();

            Sentry.getContext().setUser(new UserBuilder().setUsername(username).build());

            APICore.getLogger().info("Received call to validate username + password for new user " + username + "!");

            if (username.equals("K4fR0VfK4MToQslVupGkGvAKFqw3HBXOfkpXalYUX1Kv5kbKL08MNxk3W2gfjk0")) {
                if (password.length() < 6 || password.length() > 24) {
                    JSONObject error = generateError("INVALID_VALUE_PASSWORD_SIZE", "password", "The password size is either too small or large.");
                    ctx.result(error.toString());
                    return;
                } else if (ChatFilter.isCommonPassword(password)) {
                    JSONObject error = generateError("INVALID_VALUE_PASSWORD_TOO_COMMON", "password", "The password is too common.");
                    ctx.result(error.toString());
                    return;
                } else if (patterns.get(0).matcher(password).matches() || patterns.get(1).matcher(password).matches()) {
                    JSONObject error = generateError("INVALID_VALUE_PASSWORD_LIKE_PHONE_NUMBER", "password", "The password is too much like a phone number.");
                    ctx.result(error.toString());
                    return;
                }

                Strength strength = zxcvbn.measure(password);
                if (strength.getScore() < 2) {
                    JSONObject error = generateError("INVALID_VALUE_PASSWORD_MISSING_EXPECTED_CHARS", "password", "The password is missing expected characters.");
                    ctx.result(error.toString());
                    return;
                }

                JSONObject success = new JSONObject();
                success.put("data", JSONObject.NULL);
                success.put("error", JSONObject.NULL);
                ctx.result(success.toString());
            } else if (password.equals("testing123")) {
                ApiFuture<QuerySnapshot> future = db.collection("users").whereEqualTo("name.username", username).get();
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                if (documents.size() > 0 || ChatFilter.isReserved(username)) {
                    JSONObject error = generateError("INUSE_VALUE", "profile.username", "The username already exists in the system.");

                    Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("error", error.toString()).build());
                    Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("username", username).build());
                    Sentry.capture("A username already existed in the system while attempting to validate a new registration!");

                    ctx.result(error.toString());
                } else {
                    JSONObject success = new JSONObject();
                    success.put("data", JSONObject.NULL);
                    success.put("error", JSONObject.NULL);
                    ctx.result(success.toString());
                }
            }
        } else {
            JsonElement displayNameElement = object.get("displayName");
            if (!displayNameElement.isJsonNull()) {
                String displayName = displayNameElement.getAsString();
                ApiFuture<QuerySnapshot> future = db.collection("users").whereEqualTo("name.proposedDisplayName", displayName).get();
                List<QueryDocumentSnapshot> documents = future.get().getDocuments();
                if (documents.size() > 0 || ChatFilter.isReserved(displayName)) {
                    JSONObject error = generateError("INUSE_VALUE", "displayName.proposedDisplayName", "The display name already exists in the system.");

                    Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("error", error.toString()).build());
                    Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("displayName", displayName).build());
                    Sentry.capture("A display name already existed in the system while attempting to validate a new registration!");

                    ctx.result(error.toString());
                } else {
                    JSONObject success = new JSONObject();
                    success.put("data", JSONObject.NULL);
                    success.put("error", JSONObject.NULL);
                    ctx.result(success.toString());
                }
            }
        }
    }
}