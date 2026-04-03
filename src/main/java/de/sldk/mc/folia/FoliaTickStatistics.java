package de.sldk.mc.folia;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;

public final class FoliaTickStatistics {

    private static final long SNAPSHOT_TTL_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private static final Map<Server, FoliaTickStatistics> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static FoliaTickStatistics forServer(Server server, Logger logger) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, ignored -> new FoliaTickStatistics(server, logger));
        }
    }

    private final Server server;
    private final Reflection reflection;

    private volatile Snapshot cachedSnapshot = Snapshot.empty();
    private volatile long cachedSnapshotNanos = 0L;

    private FoliaTickStatistics(Server server, Logger logger) {
        this.server = server;
        this.reflection = new Reflection(server, logger);
    }

    public boolean isAvailable() {
        return reflection.isAvailable();
    }

    public double getAverageTps() {
        return snapshot().averageTps();
    }

    public double getAverageTickDurationNanos() {
        return snapshot().averageTickDurationNanos();
    }

    public double getMedianTickDurationNanos() {
        return snapshot().medianTickDurationNanos();
    }

    public double getMinTickDurationNanos() {
        return snapshot().minTickDurationNanos();
    }

    public double getMaxTickDurationNanos() {
        return snapshot().maxTickDurationNanos();
    }

    private Snapshot snapshot() {
        long now = System.nanoTime();
        Snapshot snapshot = cachedSnapshot;

        if (now - cachedSnapshotNanos <= SNAPSHOT_TTL_NANOS) {
            return snapshot;
        }

        synchronized (this) {
            if (now - cachedSnapshotNanos <= SNAPSHOT_TTL_NANOS) {
                return cachedSnapshot;
            }

            cachedSnapshot = loadSnapshot(now);
            cachedSnapshotNanos = now;
            return cachedSnapshot;
        }
    }

    private Snapshot loadSnapshot(long now) {
        if (!reflection.isAvailable()) {
            return Snapshot.empty();
        }

        List<RegionSnapshot> regions = new ArrayList<>();
        for (World world : server.getWorlds()) {
            reflection.collect(world, now, regions);
        }
        return Snapshot.from(regions);
    }

    private record Snapshot(List<RegionSnapshot> regions) {

        private static Snapshot empty() {
            return new Snapshot(List.of());
        }

        private static Snapshot from(List<RegionSnapshot> regions) {
            return new Snapshot(List.copyOf(regions));
        }

        private double averageTps() {
            return regions.stream()
                    .mapToDouble(RegionSnapshot::averageTps)
                    .average()
                    .orElse(20.0D);
        }

        private double averageTickDurationNanos() {
            return regions.stream()
                    .mapToDouble(RegionSnapshot::averageTickDurationNanos)
                    .average()
                    .orElse(0.0D);
        }

        private double medianTickDurationNanos() {
            return regions.stream()
                    .mapToDouble(RegionSnapshot::medianTickDurationNanos)
                    .average()
                    .orElse(0.0D);
        }

        private double minTickDurationNanos() {
            return regions.stream()
                    .mapToDouble(RegionSnapshot::minTickDurationNanos)
                    .min()
                    .orElse(0.0D);
        }

        private double maxTickDurationNanos() {
            return regions.stream()
                    .mapToDouble(RegionSnapshot::maxTickDurationNanos)
                    .max()
                    .orElse(0.0D);
        }
    }

    private record RegionSnapshot(
            double averageTps,
            double averageTickDurationNanos,
            double medianTickDurationNanos,
            double minTickDurationNanos,
            double maxTickDurationNanos) {
    }

    private static final class Reflection {

        private final Logger logger;
        private final boolean available;

        private Method getHandleMethod;
        private Field regioniserField;
        private Method computeForAllRegionsMethod;
        private Method getRegionDataMethod;
        private Method getRegionSchedulingHandleMethod;
        private Method getTickReport5sMethod;
        private Method getTickReport15sMethod;
        private Method tpsDataMethod;
        private Method timePerTickDataMethod;
        private Method segmentAllMethod;
        private Method averageMethod;
        private Method leastMethod;
        private Method greatestMethod;
        private Method medianMethod;

        private Reflection(Server server, Logger logger) {
            this.logger = logger;
            this.available = FoliaSupport.isFolia() && initialize(server);
        }

        private boolean isAvailable() {
            return available;
        }

        private boolean initialize(Server server) {
            World firstWorld = server.getWorlds().stream().findFirst().orElse(null);
            if (firstWorld == null) {
                logger.warning("Folia tick statistics are unavailable because no worlds are loaded yet.");
                return false;
            }

            try {
                getHandleMethod = firstWorld.getClass().getMethod("getHandle");

                Class<?> handleClass = getHandleMethod.getReturnType();
                regioniserField = findField(handleClass, "regioniser");
                regioniserField.setAccessible(true);

                computeForAllRegionsMethod = regioniserField.getType().getMethod("computeForAllRegions", Consumer.class);

                Class<?> threadedRegionClass =
                        Class.forName("io.papermc.paper.threadedregions.ThreadedRegionizer$ThreadedRegion");
                getRegionDataMethod = threadedRegionClass.getMethod("getData");

                Class<?> tickRegionDataClass = Class.forName("io.papermc.paper.threadedregions.TickRegions$TickRegionData");
                getRegionSchedulingHandleMethod = tickRegionDataClass.getMethod("getRegionSchedulingHandle");

                Class<?> scheduleHandleClass = getRegionSchedulingHandleMethod.getReturnType();
                getTickReport5sMethod = scheduleHandleClass.getMethod("getTickReport5s", long.class);
                getTickReport15sMethod = scheduleHandleClass.getMethod("getTickReport15s", long.class);

                Class<?> tickReportDataClass = getTickReport15sMethod.getReturnType();
                tpsDataMethod = tickReportDataClass.getMethod("tpsData");
                timePerTickDataMethod = tickReportDataClass.getMethod("timePerTickData");

                Class<?> segmentedAverageClass = tpsDataMethod.getReturnType();
                segmentAllMethod = segmentedAverageClass.getMethod("segmentAll");

                Class<?> segmentClass = segmentAllMethod.getReturnType();
                averageMethod = segmentClass.getMethod("average");
                leastMethod = segmentClass.getMethod("least");
                greatestMethod = segmentClass.getMethod("greatest");
                medianMethod = segmentClass.getMethod("median");

                return true;
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Failed to initialize Folia threaded-region tick statistics. TPS and tick duration metrics will remain disabled.");
                logger.log(Level.FINE, "Failed to initialize Folia threaded-region tick statistics.", e);
                return false;
            }
        }

        private void collect(World world, long now, List<RegionSnapshot> snapshots) {
            try {
                Object worldHandle = invoke(getHandleMethod, world);
                Object regioniser = regioniserField.get(worldHandle);
                if (regioniser == null) {
                    return;
                }

                invoke(computeForAllRegionsMethod, regioniser, (Consumer<Object>) region -> {
                    RegionSnapshot snapshot = createSnapshot(region, now);
                    if (snapshot != null) {
                        snapshots.add(snapshot);
                    }
                });
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to read Folia tick statistics for world " + world.getName(), e);
            }
        }

        private RegionSnapshot createSnapshot(Object region, long now) {
            try {
                Object regionData = invoke(getRegionDataMethod, region);
                Object scheduleHandle = invoke(getRegionSchedulingHandleMethod, regionData);

                Object durationReport = invoke(getTickReport5sMethod, scheduleHandle, now);
                Object tpsReport = invoke(getTickReport15sMethod, scheduleHandle, now);

                if (durationReport == null && tpsReport == null) {
                    return null;
                }

                double averageTps = tpsReport == null ? 20.0D : readStatistic(tpsReport, tpsDataMethod, averageMethod);
                double averageDuration = durationReport == null ? 0.0D : readStatistic(durationReport, timePerTickDataMethod, averageMethod);
                double medianDuration = durationReport == null ? 0.0D : readStatistic(durationReport, timePerTickDataMethod, medianMethod);
                double minDuration = durationReport == null ? 0.0D : readStatistic(durationReport, timePerTickDataMethod, leastMethod);
                double maxDuration = durationReport == null ? 0.0D : readStatistic(durationReport, timePerTickDataMethod, greatestMethod);

                return new RegionSnapshot(averageTps, averageDuration, medianDuration, minDuration, maxDuration);
            } catch (Exception e) {
                logger.log(Level.FINE, "Failed to read Folia tick statistics for a region.", e);
                return null;
            }
        }

        private double readStatistic(Object report, Method dataMethod, Method statisticMethod) throws ReflectiveOperationException {
            Object segmentedAverage = invoke(dataMethod, report);
            Object segment = invoke(segmentAllMethod, segmentedAverage);
            Object value = invoke(statisticMethod, segment);

            if (value instanceof Number number) {
                return number.doubleValue();
            }

            throw new IllegalStateException("Unexpected tick statistic type: " + value);
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> currentType = type;
            while (currentType != null) {
                try {
                    return currentType.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    currentType = currentType.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }

        private static Object invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                    throw reflectiveOperationException;
                }
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new RuntimeException(cause);
            }
        }
    }
}
