package de.sldk.mc.folia;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

public final class FoliaSupport {

    private FoliaSupport() {
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static CompletableFuture<Void> runSync(Plugin plugin, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            if (isFolia()) {
                executeGlobal(plugin.getServer(), plugin, () -> {
                    task.run();
                    future.complete(null);
                });
            } else {
                plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                    task.run();
                    future.complete(null);
                    return null;
                });
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public static CompletableFuture<Void> runAsync(Plugin plugin, Runnable task) {
        if (!isFolia()) {
            return CompletableFuture.runAsync(task);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            executeAsync(plugin.getServer(), plugin, () -> {
                task.run();
                future.complete(null);
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private static void executeGlobal(Server server, Plugin plugin, Runnable task) throws ReflectiveOperationException {
        Object scheduler = server.getClass().getMethod("getGlobalRegionScheduler").invoke(server);
        Method executeMethod = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
        invoke(executeMethod, scheduler, plugin, task);
    }

    private static void executeAsync(Server server, Plugin plugin, Runnable task) throws ReflectiveOperationException {
        Object scheduler = server.getClass().getMethod("getAsyncScheduler").invoke(server);
        Method runNowMethod = scheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
        invoke(runNowMethod, scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run());
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
