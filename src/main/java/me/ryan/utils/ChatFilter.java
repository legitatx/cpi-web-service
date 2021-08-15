package me.legit.utils;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import me.legit.APICore;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class ChatFilter {

    private static final Set<String> CURSES = new HashSet<>();
    private static final Set<String> NAMES = new HashSet<>();
    private static final Set<String> COMMON_PASSWORDS = new HashSet<>();
    private static final Map<String, String> TRANSLATION = new HashMap<>();

    private static final Pattern ipPattern = Pattern.compile("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)");
    private static final Pattern urlPattern = Pattern.compile("^(?:((s?ftps?)|(https?))://)?(([-\\w_]{1,63}\\.)+[a-zA-Z]{2,63})(/\\S*)?$");

    static {
        DocumentReference docRef = APICore.getDatabaseManager().firebase().getFirestore()
                .collection("chatFilter")
                .document("swearWords");
        ApiFuture<DocumentSnapshot> future = docRef.get();

        try {
            DocumentSnapshot document = future.get();

            if (document.exists()) {
                System.out.println("Initializing chat filter...");
                List<String> words = (ArrayList<String>) document.get("disallowedWords");
                if (words != null) {
                    CURSES.addAll(words);
                } else {
                    System.out.println("Chat filter words could not be found! Please resolve this before attempting to start again. Shutting down... (words == null)");
                    System.exit(-1);
                }
            } else {
                System.out.println("Chat filter words could not be found! Please resolve this before attempting to start again. Shutting down... (document.exists() == false)");
                System.exit(-1);
            }
        } catch (Exception e) {
            System.out.println("A fatal error occurred while attempting to initialize the chat filter! Shutting down...");
            e.printStackTrace();

            System.exit(-1);
        }

        ClassLoader loader = ChatFilter.class.getClassLoader();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(loader.getResourceAsStream("10-million-password-list-top-10000.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                COMMON_PASSWORDS.add(line);
            }
        } catch (IOException e) {
            System.out.println("A fatal error occurred while attempting to read common passwords list! Shutting down...");
            e.printStackTrace();

            System.exit(-1);
        }

        TRANSLATION.put("@", "a");
        TRANSLATION.put("4", "a");
        TRANSLATION.put("3", "e");
        TRANSLATION.put("(", "9");
        TRANSLATION.put("5", "s");
        TRANSLATION.put("0", "o");
        TRANSLATION.put("()", "o");
        TRANSLATION.put("1", "i");
        TRANSLATION.put("!", "l");
        TRANSLATION.put("*", "a");
        TRANSLATION.put("$", "s");
        TRANSLATION.put("ã", "a");
        TRANSLATION.put("õ", "o");
        TRANSLATION.put("á", "a");
        TRANSLATION.put("ó", "o");
        TRANSLATION.put("é", "e");
        TRANSLATION.put("ú", "u");
        TRANSLATION.put("Ａ", "a");
        TRANSLATION.put("Ｂ", "b");
        TRANSLATION.put("Ｃ", "c");
        TRANSLATION.put("Ｄ", "d");
        TRANSLATION.put("Ｅ", "e");
        TRANSLATION.put("Ｆ", "f");
        TRANSLATION.put("Ｇ", "g");
        TRANSLATION.put("Ｈ", "h");
        TRANSLATION.put("Ｉ", "i");
        TRANSLATION.put("Ｊ", "j");
        TRANSLATION.put("Ｋ", "k");
        TRANSLATION.put("Ｌ", "l");
        TRANSLATION.put("Ｍ", "m");
        TRANSLATION.put("Ｎ", "n");
        TRANSLATION.put("Ｏ", "o");
        TRANSLATION.put("Ｐ", "p");
        TRANSLATION.put("Ｑ", "q");
        TRANSLATION.put("Ｒ", "r");
        TRANSLATION.put("Ｓ", "s");
        TRANSLATION.put("Ｔ", "t");
        TRANSLATION.put("Ｕ", "u");
        TRANSLATION.put("Ｖ", "v");
        TRANSLATION.put("Ｗ", "w");
        TRANSLATION.put("Ｘ", "x");
        TRANSLATION.put("Ｙ", "y");
        TRANSLATION.put("Ｚ", "z");
        TRANSLATION.put("•", ".");
        TRANSLATION.put("_", " ");

        NAMES.add("Merry Walrus");
        NAMES.add("Mai Jalapeño");
        NAMES.add("Bacon Pie");
        NAMES.add("Cadence");
        NAMES.add("Herbert P. Bear");
        NAMES.add("HardwareHoser");
        NAMES.add("M0r3 C0wb3ll");
        NAMES.add("Nopenny2000");
        NAMES.add("Dory");
        NAMES.add("Rhubarbcrmbl");
        NAMES.add("Ninja");
        NAMES.add("McKenzie");
        NAMES.add("Protobot");
        NAMES.add("NightFuryQueen");
        NAMES.add("Loustik005");
        NAMES.add("Gamma Gal");
        NAMES.add("Robo Bird");
        NAMES.add("Gajotz");
        NAMES.add("Someday");
        NAMES.add("SargeSparkles");
        NAMES.add("Suzisnoball");
        NAMES.add("Tigerwolf");
        NAMES.add("Mr Slumpy");
        NAMES.add("Cool Beans");
        NAMES.add("StackOverflo");
        NAMES.add("Fun Feathers");
        NAMES.add("The Inquisitor");
        NAMES.add("Elite Puffles");
        NAMES.add("Pirate Crabs");
        NAMES.add("SprtnAntrctcn");
        NAMES.add("PrettyFlyy");
        NAMES.add("G Billy");
        NAMES.add("McBeaks");
        NAMES.add("Is Unicorn");
        NAMES.add("Lolz");
        NAMES.add("IslandQueen");
        NAMES.add("Cloudflame");
        NAMES.add("Dtest104");
        NAMES.add("Polar Play");
        NAMES.add("Candomba13");
        NAMES.add("Sabine Wren");
        NAMES.add("Businesmoose");
        NAMES.add("Mars Hawk");
        NAMES.add("AdmiralSqueak");
        NAMES.add("Bobmij");
        NAMES.add("Stompin Bob");
        NAMES.add("Emma");
        NAMES.add("RocketSausage");
        NAMES.add("Zeb Orrelios");
        NAMES.add("Mustache");
        NAMES.add("Nick Wilde");
        NAMES.add("Stomping Bob");
        NAMES.add("Rainingchamp");
        NAMES.add("CodingMan");
        NAMES.add("Tourdude");
        NAMES.add("CeCe");
        NAMES.add("Artysaurus");
        NAMES.add("Rainbowfleur");
        NAMES.add("Tusk");
        NAMES.add("Marshmallow");
        NAMES.add("Franky");
        NAMES.add("Smulley");
        NAMES.add("Pizza22");
        NAMES.add("EmAgain");
        NAMES.add("Skidder");
        NAMES.add("Mmm Cookies");
        NAMES.add("TanookiSuit");
        NAMES.add("Chilly Wonka");
        NAMES.add("Pony Hawk");
        NAMES.add("Rockhopper");
        NAMES.add("Deelitedansa");
        NAMES.add("Solista");
        NAMES.add("Sophye Jolie");
        NAMES.add("Keeper of the Stage");
        NAMES.add("Frozentweety");
        NAMES.add("SGT Sparkles");
        NAMES.add("Scorn");
        NAMES.add("BubbaHotep01");
        NAMES.add("Megg");
        NAMES.add("Meggasaurus");
        NAMES.add("Sophistikat");
        NAMES.add("Constantine");
        NAMES.add("Gariwald");
        NAMES.add("Olaf");
        NAMES.add("Squeakynose");
        NAMES.add("SunnyPacPac");
        NAMES.add("Zendaya");
        NAMES.add("Karinjitus");
        NAMES.add("Violetta");
        NAMES.add("Screenhog");
        NAMES.add("Lion Mane");
        NAMES.add("Amora");
        NAMES.add("Stompin' Bob");
        NAMES.add("Lilblue Leia");
        NAMES.add("Funkychick3n");
        NAMES.add("Keeper of the Boiler Room");
        NAMES.add("Anna");
        NAMES.add("Kermit");
        NAMES.add("Gary");
        NAMES.add("Chattabox");
        NAMES.add("SmandrewDoll");
        NAMES.add("EmperorRosie");
        NAMES.add("Jackalope22");
        NAMES.add("Waddledodds");
        NAMES.add("Captain Pirate");
        NAMES.add("Federflink1");
        NAMES.add("Polite Panda");
        NAMES.add("Chai Cookie");
        NAMES.add("thebruce");
        NAMES.add("Purplelala1");
        NAMES.add("Hera Syndulla");
        NAMES.add("Pixie Pi");
        NAMES.add("Pirate");
        NAMES.add("Sabrina Carpenter");
        NAMES.add("Mr Toph");
        NAMES.add("Green Amber");
        NAMES.add("Judy Hopps");
        NAMES.add("Jenguin3000");
        NAMES.add("Deamama");
        NAMES.add("Polo Field");
        NAMES.add("Happy77");
        NAMES.add("Kezacoa");
        NAMES.add("Happyllama");
        NAMES.add("Test Snail");
        NAMES.add("Ezra Bridger");
        NAMES.add("Iwearbowties");
        NAMES.add("Shibe");
        NAMES.add("Aquarium Bob");
        NAMES.add("Amykim");
        NAMES.add("Sly");
        NAMES.add("Rocky");
        NAMES.add("Skaboots");
        NAMES.add("DisneyJackson");
        NAMES.add("Snego Lex");
        NAMES.add("Cole Plante");
        NAMES.add("Maplesyrup16");
        NAMES.add("Cupcakehands");
        NAMES.add("Dot");
        NAMES.add("Nerdy");
        NAMES.add("Tank");
        NAMES.add("Espresso87");
        NAMES.add("5feet2short");
        NAMES.add("Intothemoat");
        NAMES.add("Beakermeep");
        NAMES.add("Mosski");
        NAMES.add("Cr3amSoda2");
        NAMES.add("Mal");
        NAMES.add("Shadow Kat");
        NAMES.add("Stackoverflo");
        NAMES.add("Klutzy");
        NAMES.add("Ooompah");
        NAMES.add("Brittobella");
        NAMES.add("Gizmo");
        NAMES.add("Ladypickles");
        NAMES.add("Yarr");
        NAMES.add("Rory");
        NAMES.add("Rootinhootin");
        NAMES.add("The Muppets");
        NAMES.add("Skabee");
        NAMES.add("GoldenStallion");
        NAMES.add("Light Petal");
        NAMES.add("Ceddster");
        NAMES.add("Super Sheep");
        NAMES.add("Wafflepiano");
        NAMES.add("NoFunAlex");
        NAMES.add("Bambalou");
        NAMES.add("Pete");
        NAMES.add("Tato Maxx");
        NAMES.add("Rainbofeet");
        NAMES.add("Jet Pack Guy");
        NAMES.add("Jujubolinha9");
        NAMES.add("Scrap");
        NAMES.add("Penny Pebbles");
        NAMES.add("Mr N00dle");
        NAMES.add("Nickname1");
        NAMES.add("Nickname2");
        NAMES.add("Elsa");
        NAMES.add("Nickname3");
        NAMES.add("Nickname4");
        NAMES.add("Lilac Ren");
        NAMES.add("Bxplosion");
        NAMES.add("Lola Krayola");
        NAMES.add("SheepVsGravity");
        NAMES.add("Cheese Tree");
        NAMES.add("Petey K");
        NAMES.add("Soezbruh");
        NAMES.add("Rsnail");
        NAMES.add("An Engineer");
        NAMES.add("Styoma Storm");
        NAMES.add("SunnyFlowerx");
        NAMES.add("Brady");
        NAMES.add("RicanViking");
        NAMES.add("Snomin");
        NAMES.add("Goodtea");
        NAMES.add("FrostyApten");
        NAMES.add("Sunrainbows");
        NAMES.add("Sam the Sasquatch");
        NAMES.add("Spike Hike");
        NAMES.add("Grand Stand");
        NAMES.add("Yamfry");
        NAMES.add("Crayona");
        NAMES.add("Eatyaveggies");
        NAMES.add("TDizzle");
        NAMES.add("Grasstain");
        NAMES.add("Blu3st33l");
        NAMES.add("Shadow Guy");
        NAMES.add("Maytukka");
        NAMES.add("C00kie Dough");
        NAMES.add("Koa Koralle");
        NAMES.add("Billybob");
        NAMES.add("Sir Champion");
        NAMES.add("Skip");
        NAMES.add("Baseballfan9");
        NAMES.add("Rosefast");
        NAMES.add("Cool Times");
        NAMES.add("Brockmann");
        NAMES.add("Tulip4zul");
        NAMES.add("Chowler");
        NAMES.add("CaptainLu");
        NAMES.add("Chaddington");
        NAMES.add("Polo");
        NAMES.add("King Sheep");
        NAMES.add("Karlapop321");
        NAMES.add("Darwin");
        NAMES.add("Steampowered");
        NAMES.add("Груша");
        NAMES.add("Lemonylimepi");
        NAMES.add("Test Bots");
        NAMES.add("Tarlic Goast");
        NAMES.add("Tour Guide");
        NAMES.add("Kanan Jarrus");
        NAMES.add("Aunt Arctic");
        NAMES.add("Swampy");
        NAMES.add("Boltzor");
        NAMES.add("Rookie");
        NAMES.add("Sotcher");
        NAMES.add("Shrub Shrub");
        NAMES.add("Glitterbug K");
        NAMES.add("Снежинка");
        NAMES.add("Rock Opera");
        NAMES.add("Ilsoap");
        NAMES.add("PH");
        NAMES.add("Wolfyjumps");
        NAMES.add("Luv2dance160");
        NAMES.add("Sensei");
    }

    public ChatFilter(Firestore db) {
        listenForChanges(db);
    }

    private void listenForChanges(Firestore db) {
        DocumentReference ref = db.collection("swearWords").document("disallowedWords");
        ref.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                APICore.getLogger().severe("Listen failed: " + error);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                APICore.getLogger().info("Got new data for chat filter: " + snapshot.getData());

                List<String> words = (ArrayList<String>) snapshot.get("disallowedWords");

                if (words != null) {
                    //CURSES.clear();
                    CURSES.addAll(words);
                }
            }
        });
    }

    public boolean isMessageDisallowed(String message) {
        return containsSwear(message) || containsSpam(message);
    }

    private static String getPlainText(String message) {
        message = message.toLowerCase().trim();
        StringBuilder stringBuilder = new StringBuilder();
        for (char letter : message.toCharArray()) {
            if (Character.isLetter(letter) || Character.isDigit(letter) || letter == ' ') {
                stringBuilder.append(letter);
            }
        }
        String output = stringBuilder.toString();
        for (String key : TRANSLATION.keySet()) {
            output = output.replace(key, TRANSLATION.get(key));
        }
        return output;
    }

    private boolean containsSwear(String message) {
        String[] split = getPlainText(message).split(" ");

        for (String entry : split) {
            StringBuilder strippedEntry = new StringBuilder();

            Character lastCharacter = null;

            for (int x = 0; x < entry.length(); x++) {
                char current = entry.charAt(x);

                if (lastCharacter != null && lastCharacter == current) {
                    continue;
                }

                lastCharacter = current;

                strippedEntry.append(current);
            }

            if (strippedEntry.length() > 0 && CURSES.contains(strippedEntry.toString())) {
                return true;
            }
        }

        for (String word : split) {
            if (CURSES.contains(word)) {
                return true;
            } else if (word.endsWith("s") && CURSES.contains(word.substring(0, word.length() - 1))) {
                return true;
            }
        }

        outerLoop:
        for (int i = 0; i < split.length - 1; i++) {
            String outer = split[i];
            if (outer.length() > 5) {
                continue;
            }
            for (int j = 0; j < split.length - i; j++) {
                StringBuilder sb = new StringBuilder(outer);
                for (int k = i + 1; k <= i + j; k++) {
                    if (split[k].length() > 5) {
                        continue outerLoop;
                    }
                    sb.append(split[k]);
                }
                if (CURSES.contains(sb.toString())) {
                    return true;
                }
            }
        }

        return false;
    }

    private Boolean containsSpam(String message) {
        message = message.toLowerCase();
        if (message.length() > 8) {
            HashMap<Character, Integer> counts = new HashMap<>();
            for (Character c : message.toLowerCase().toCharArray()) {
                if (!counts.containsKey(c)) {
                    counts.put(c, 1);
                } else {
                    counts.put(c, counts.get(c) + 1);
                }
            }
            for (Integer i : counts.values()) {
                if ((i.doubleValue() / message.length()) >= 0.6) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Boolean isReserved(String nameToVerify) {
        for (String name : NAMES) {
            if (name.toLowerCase().equals(nameToVerify.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static Boolean isCommonPassword(String passwordToVerify) {
        return COMMON_PASSWORDS.contains(passwordToVerify.toLowerCase());
    }
}
