/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.AppConfigLoader;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.ClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import io.prometheus.client.exporter.HTTPServer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class App implements Runnable {

  protected static ClientInterceptor monitoringInterceptor;
  protected static PrometheusMeterRegistry prometheusRegistry;
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(App.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(App.class);
  private static HTTPServer monitoringServer;

  static void createApp(final Function<AppCfg, Runnable> appFactory) {
    final AppCfg appCfg = AppConfigLoader.load();
    startMonitoringServer(appCfg);
    Runtime.getRuntime().addShutdownHook(new Thread(App::stopMonitoringServer));

    appFactory.apply(appCfg).run();
  }

  private static void startMonitoringServer(final AppCfg appCfg) {
    prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    prometheusRegistry.config().meterFilter(new PrometheusRenameFilter());

    try {
      // you can set the daemon flag to false if you want the server to block
      monitoringServer =
          new HTTPServer(
              new InetSocketAddress(appCfg.getMonitoringPort()),
              prometheusRegistry.getPrometheusRegistry(),
              true);
    } catch (final IOException e) {
      LOG.error("Problem on starting monitoring server.", e);
    }

    monitoringInterceptor = new MetricCollectingClientInterceptor(prometheusRegistry);
  }

  private static void stopMonitoringServer() {
    if (monitoringServer != null) {
      monitoringServer.stop();
      monitoringServer = null;
    }
  }

  void printTopology(final ZeebeClient client) {
    while (true) {
      try {
        final Topology topology = client.newTopologyRequest().send().join();
        topology
            .getBrokers()
            .forEach(
                b -> {
                  LOG.info("Broker {} - {}", b.getNodeId(), b.getAddress());
                  b.getPartitions()
                      .forEach(p -> LOG.info("{} - {}", p.getPartitionId(), p.getRole()));
                });
        break;
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Topology request failed", e);
      }
    }
  }

  protected String readVariables(final String payloadPath) {
    try {
      final var classLoader = App.class.getClassLoader();
      try (final InputStream fromResource = classLoader.getResourceAsStream(payloadPath)) {
        if (fromResource != null) {
          return tryReadVariables(fromResource);
        }
        // unable to find from resources, try as regular file
        try (final InputStream fromFile = new FileInputStream(payloadPath)) {
          return tryReadVariables(fromFile);
        }
      }
    } catch (final IOException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  private String tryReadVariables(final InputStream inputStream) throws IOException {
    final StringBuilder stringBuilder = new StringBuilder();
    try (final InputStreamReader reader = new InputStreamReader(inputStream)) {
      try (final BufferedReader br = new BufferedReader(reader)) {
        String line;
        while ((line = br.readLine()) != null) {
          stringBuilder.append(line).append("\n");
        }
      }
    }
    return stringBuilder.toString();
  }
}
