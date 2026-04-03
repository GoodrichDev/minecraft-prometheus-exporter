package de.sldk.mc;

import org.eclipse.jetty.http.pathmap.PathSpec;
import de.sldk.mc.health.HealthChecks;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.PathMappingsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;

public class MetricsServer {

    private final String host;
    private final int port;
    private final Plugin plugin;
	private final HealthChecks healthChecks;

    private Server server;

    public MetricsServer(String host, int port, Plugin plugin, HealthChecks healthChecks) {
        this.host = host;
        this.port = port;
        this.plugin = plugin;
        this.healthChecks = healthChecks;
	}

    public void start() throws Exception {
        GzipHandler gzipHandler = new GzipHandler();

        var pathMappings = new PathMappingsHandler();
        pathMappings.addMapping(PathSpec.from("/metrics"), MetricsController.create(plugin));
        pathMappings.addMapping(PathSpec.from("/health"), HealthController.create(healthChecks));

        gzipHandler.setHandler(pathMappings);

        InetSocketAddress address = new InetSocketAddress(host, port);
        server = new Server(address);
        server.setHandler(gzipHandler);

        server.start();
    }

    public void stop() throws Exception {
        if (server == null) {
            return;
        }

        server.stop();
    }
}
