package re.imc.geysermodelengine;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/** A stand-in for the BetterModel plugin, which BetterModel's tracker classes ask for when they load. */
public final class BetterModelStub {

    private static boolean installed;

    private BetterModelStub() {
    }

    /** Call before any BetterModel tracker class is loaded (a mocked one included). */
    public static synchronized void install() {
        if (installed) return;
        BetterModel.register(mock(BetterModelPlatform.class, RETURNS_DEEP_STUBS));
        installed = true;
    }
}
