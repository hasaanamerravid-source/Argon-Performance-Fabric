package argon;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Argon implements ModInitializer {
    public static final String VERSION = "0.2.2";
    public static final Logger LOGGER = LoggerFactory.getLogger("Argon");

    @Override
    public void onInitialize() {
        LOGGER.info("Argon {} layout optimizer", VERSION);
    }
}
