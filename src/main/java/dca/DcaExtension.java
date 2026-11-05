package dca;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.CarpetSettings;
import carpet.api.settings.SettingsManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * DCA - a Carpet extension.
 *
 * <p>How a Carpet extension works, in three steps:
 * <ol>
 *   <li>Implement {@link CarpetExtension}. Fabric Loader creates this class because it is listed
 *       under the {@code main} entrypoint in {@code fabric.mod.json}.</li>
 *   <li>In {@link #onInitialize()} (a plain Fabric {@link ModInitializer} callback) call
 *       {@link CarpetServer#manageExtension(CarpetExtension)} so Carpet knows about us. This must
 *       happen before the server starts, which is exactly what a {@code main} entrypoint gives us.</li>
 *   <li>Register our settings class in {@link #onGameStarted()} and hand Carpet the
 *       {@link SettingsManager} we want our rules to live in. Carpet then creates the
 *       {@code /dca ...} command and reads/writes {@code <world>/dca.conf} for us.</li>
 * </ol>
 *
 * <p>Rules themselves are just annotated {@code public static} fields - see {@link DcaSettings}.
 */
public class DcaExtension implements CarpetExtension, ModInitializer {
    public static final String MOD_ID = "dca";
    public static final String MOD_NAME = "DCA";
    public static final String VERSION = "1.0.3";

    /**
     * Our own settings manager. Handing it to Carpet (via {@link #extensionSettingsManager()})
     * gives us a dedicated {@code /dca <rule> [value]} command plus {@code <world>/dca.conf}.
     * If you would rather have your rules show up in the vanilla {@code /carpet} command, skip this
     * and register your settings class with
     * {@code CarpetServer.settingsManager.parseSettingsClass(YourSettings.class)} instead.
     */
    private static final SettingsManager SETTINGS = new SettingsManager(VERSION, MOD_ID, MOD_NAME);

    /** Called from Fabric Loader's {@code main} entrypoint. */
    public static void loadExtension() {
        CarpetServer.manageExtension(new DcaExtension());
    }

    @Override
    public void onInitialize() {
        loadExtension();
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public SettingsManager extensionSettingsManager() {
        return SETTINGS;
    }

    @Override
    public void onGameStarted() {
        // Scans DcaSettings for @Rule fields and turns them into /dca rules.
        SETTINGS.parseSettingsClass(DcaSettings.class);
    }

    /**
     * Rule names/descriptions shown by the {@code /dca} command and its clickable menus come from
     * here. Carpet asks us for the language the user selected and expects a flat
     * {@code key -> text} map.
     *
     * <p>Key format (see carpet's TranslationKeys):
     * {@code <managerId>.rule.<ruleName>.name} / {@code .desc} and {@code <managerId>.category.<category>}.
     */
    @Override
    public Map<String, String> canHasTranslations(String lang) {
        String path = "assets/" + MOD_ID + "/lang/" + lang + ".json";
        try (InputStream in = DcaExtension.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return Collections.emptyMap();   // we simply don't have that language
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = new GsonBuilder().setLenient().create();   // lenient: allows comments
            Map<String, String> map = gson.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            CarpetSettings.LOG.warn("[{}] failed to load translations for '{}'", MOD_NAME, lang, e);
            return Collections.emptyMap();
        }
    }
}
