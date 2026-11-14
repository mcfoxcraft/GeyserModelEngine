package re.imc.geysermodelengine.runnables;

import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.Model;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;

public class UpdateTaskRunnable implements Runnable {

    /** A failure is logged with its stack trace at most this often; the ones in between are counted. */
    static final long REPORT_EVERY_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final GeyserModelEngine plugin;
    private final LongSupplier clock;

    // Only this task touches these: its runs never overlap.
    private boolean reported = false;
    private long lastReport;
    private int unreported = 0;

    public UpdateTaskRunnable(GeyserModelEngine plugin) {
        this(plugin, System::nanoTime);
    }

    UpdateTaskRunnable(GeyserModelEngine plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    /**
     * Sends every copy's animation properties to its viewers. A periodic task that throws is never run again, which
     * would stop the animations of every model for Bedrock players until a restart: so nothing leaves this method,
     * and a copy that fails does not hold up the others.
     */
    @Override
    public void run() {
        try {
            for (Map<Model, EntityData> models : plugin.getModelManager().getEntitiesCache().values()) {
                for (EntityData entityData : models.values()) {
                    try {
                        if (entityData.getViewers().isEmpty()) continue;
                        plugin.getEntityTaskManager().getPropertyHandler().updateEntityProperties(entityData, entityData.getViewers(), false);
                    } catch (Throwable err) {
                        report(err);
                    }
                }
            }
        } catch (Throwable err) {
            report(err);
        }
    }

    private void report(Throwable err) {
        long now = clock.getAsLong();
        if (reported && now - lastReport < REPORT_EVERY_NANOS) {
            unreported++;
            return;
        }

        String more = unreported > 0 ? " (" + unreported + " more failures since the last one logged)" : "";
        plugin.getLogger().log(Level.WARNING, "Could not update the animations of a model for Bedrock players" + more, err);
        reported = true;
        lastReport = now;
        unreported = 0;
    }
}
