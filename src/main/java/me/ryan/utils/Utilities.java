package me.legit.utils;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.firebase.auth.FirebaseToken;
import me.legit.APICore;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Utilities {

    private static final SecretKey SHA256_KEY = new SecretKeySpec("5aZde+G+3e523nAhaNq^k7h7*sjM6Tb-zy6FRRvW7@v6F_gZ???&M-@W_&&cpgK7".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private static HashFunction HASH_FUNCTION = Hashing.hmacSha256(SHA256_KEY);

    public static String getPlayerUidFromName(Firestore dbInstance, String username) throws Exception {
        ApiFuture<QuerySnapshot> future = dbInstance.collection("users").whereEqualTo("name.displayName", username).limit(1).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        return documents.size() > 0 ? documents.get(0).getId() : null;
    }

    public static boolean accessedFrom(Class<?> clazz) {
        return Utilities.caller(1).getClassName().equals(clazz.getName());
    }

    public static StackTraceElement caller(int offset) {
        Preconditions.checkArgument(offset >= 0, "Offset must be a positive number");
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        if (elements.length < 4 + offset) {
            throw new IndexOutOfBoundsException("Offset too large for current stack");
        }
        return elements[3 + offset];
    }

    public static JSONObject generateError(String code, String inputName, String developerMessage) {
        JSONObject error = new JSONObject();

        error.put("data", JSONObject.NULL);

        JSONObject errorObj = new JSONObject();
        errorObj.put("keyCategory", "FAILURE_BY_DESIGN");
        errorObj.put("conversationId", JSONObject.NULL);
        errorObj.put("correlationId", UUID.randomUUID());

        JSONArray errors = new JSONArray();
        JSONObject usernameError = new JSONObject();
        usernameError.put("code", code);
        usernameError.put("category", "FAILURE_BY_DESIGN");
        usernameError.put("inputName", inputName);
        usernameError.put("errorId", UUID.randomUUID());
        usernameError.put("timestamp", Timestamp.now().toString());
        usernameError.put("data", JSONObject.NULL);
        usernameError.put("developerMessage", developerMessage);
        errors.put(usernameError);

        errorObj.put("errors", errors);

        error.put("error", errorObj);

        return error;
    }

    public static JSONObject generateResponse(FirebaseToken decodedToken, String accessToken, String refreshToken) {
        JSONObject finalObject = new JSONObject();

        JSONObject data = new JSONObject();
        data.put("etag", "");

        JSONObject profile = new JSONObject();
        profile.put("swid", decodedToken.getUid());
        profile.put("username", decodedToken.getName());
        profile.put("parentEmail", decodedToken.getEmail());
        profile.put("firstName", decodedToken.getName());
        profile.put("ageBand", "CHILD");
        profile.put("ageBandAssumed", true);
        profile.put("countryCodeDetected", "US");
        profile.put("status", "ACTIVE");
        data.put("profile", profile);

        JSONObject token = new JSONObject();
        token.put("access_token", accessToken);
        token.put("refresh_token", refreshToken);
        token.put("swid", decodedToken.getUid());
        data.put("token", token);

        finalObject.put("data", data);
        finalObject.put("error", JSONObject.NULL);

        return finalObject;
    }

    public static JSONObject generateAvatarObj() {
        JSONObject avatar = new JSONObject();
        long avatarId = new BigInteger(49, ThreadLocalRandom.current()).longValue();
        avatar.put("AvatarId", avatarId);
        avatar.put("SlotId", 0);

        JSONObject accessory = new JSONObject();
        accessory.put("SelectionKey", "3");
        accessory.put("TintIndex", 17);
        accessory.put("XOffset", 0.0);
        accessory.put("YOffset", 0.0);
        avatar.put("Accessory", accessory);

        JSONObject brow = new JSONObject();
        brow.put("SelectionKey", "9");
        brow.put("TintIndex", 0);
        brow.put("XOffset", 0.0);
        brow.put("YOffset", 0.00417972784489393);
        avatar.put("Brow", brow);

        JSONObject costume = new JSONObject();
        costume.put("SelectionKey", "15");
        costume.put("TintIndex", 0);
        costume.put("XOffset", 0.0);
        costume.put("YOffset", 0.0);
        avatar.put("Costume", costume);

        JSONObject eyes = new JSONObject();
        eyes.put("SelectionKey", "54");
        eyes.put("TintIndex", 11);
        eyes.put("XOffset", 0.0);
        eyes.put("YOffset", 0.0121355056762695);
        avatar.put("Eyes", eyes);

        JSONObject hair = new JSONObject();
        hair.put("SelectionKey", "69");
        hair.put("TintIndex", 7);
        hair.put("XOffset", 0.0);
        hair.put("YOffset", 0.0);
        avatar.put("Hair", hair);

        JSONObject hat = new JSONObject();
        hat.put("SelectionKey", "257");
        hat.put("TintIndex", 0);
        hat.put("XOffset", 0.0);
        hat.put("YOffset", 0.0);
        avatar.put("Hat", hat);

        JSONObject nose = new JSONObject();
        nose.put("SelectionKey", "106");
        nose.put("TintIndex", 0);
        nose.put("XOffset", 0.0);
        nose.put("YOffset", 0.0116169452667236);
        avatar.put("Nose", nose);

        JSONObject mouth = new JSONObject();
        mouth.put("SelectionKey", "194");
        mouth.put("TintIndex", 0);
        mouth.put("XOffset", -0.00126063823699951);
        mouth.put("YOffset", 0.0);
        avatar.put("Mouth", mouth);

        JSONObject skin = new JSONObject();
        skin.put("SelectionKey", "123");
        skin.put("TintIndex", 9);
        skin.put("XOffset", 0.0);
        skin.put("YOffset", 0.0);
        avatar.put("Skin", skin);

        return avatar;
    }

    public static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return r.nextInt((max - min) + 1) + min;
    }

    public static long getDateDifference(Date oldDate, Date newDate, TimeUnit timeUnit) {
        long millis = newDate.getTime() - oldDate.getTime();
        return timeUnit.convert(millis, TimeUnit.MILLISECONDS);
    }

    public static HashFunction getSignatureGenerator() {
        return HASH_FUNCTION;
    }
}
