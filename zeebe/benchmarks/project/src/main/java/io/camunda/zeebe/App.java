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
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.AppConfigLoader;
import io.camunda.zeebe.config.AuthCfg.AuthType;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import io.grpc.ClientInterceptor;
import io.grpc.Status.Code;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusRenameFilter;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class App implements Runnable {

  protected static ClientInterceptor monitoringInterceptor;
  protected static PrometheusMeterRegistry registry;
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(App.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(App.class);
  private static HTTPServer monitoringServer;
  protected final AppCfg config;

  protected App(final AppCfg config) {
    this.config = config;
  }

  static void createApp(final Function<AppCfg, Runnable> appFactory) {
    final AppCfg appCfg = AppConfigLoader.load();
    startMonitoringServer(appCfg);
    Runtime.getRuntime().addShutdownHook(new Thread(App::stopMonitoringServer));

    appFactory.apply(appCfg).run();
  }

  private static void startMonitoringServer(final AppCfg appCfg) {
    registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    registry.config().meterFilter(new PrometheusRenameFilter());

    try {
      // you can set the daemon flag to false if you want the server to block
      monitoringServer =
          HTTPServer.builder()
              .port(appCfg.getMonitoringPort())
              .registry(registry.getPrometheusRegistry())
              .buildAndStart();
    } catch (final IOException e) {
      LOG.error("Problem on starting monitoring server.", e);
    }

    monitoringInterceptor = new MetricCollectingClientInterceptor(registry);
    registerDefaultInstrumentation();
  }

  @SuppressWarnings("resource") // closeable metrics will be closed when the registry is closed
  private static void registerDefaultInstrumentation() {
    new ClassLoaderMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    new JvmGcMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
  }

  private static void stopMonitoringServer() {
    if (monitoringServer != null) {
      monitoringServer.stop();
      monitoringServer = null;
    }
  }

  void printTopology(final CamundaClient client) {
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
      } catch (final ClientStatusException e) {
        final var statusCode = e.getStatusCode();
        if (statusCode.equals(Code.UNAUTHENTICATED) || statusCode.equals(Code.PERMISSION_DENIED)) {
          LOG.error(
              "Failed to retrieve topology due to authentication error; check your config", e);
          System.exit(1);
        }
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Topology request failed", e);
      }
    }
  }

  protected CamundaClientBuilder newClientBuilder() {
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .grpcAddress(URI.create(config.getBrokerUrl()))
            .restAddress(URI.create(config.getBrokerRestUrl()))
            .preferRestOverGrpc(config.isPreferRest())
            .withProperties(System.getProperties())
            .withInterceptors(monitoringInterceptor);

    if (!config.isTls()) {
      builder.usePlaintext();
    }

    final var credentialsProvider =
        switch (config.getAuth().getType()) {
          case NONE -> new NoopCredentialsProvider();
          case BASIC ->
              CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                  .username(config.getAuth().getBasic().getUsername())
                  .password(config.getAuth().getBasic().getPassword())
                  .applyEnvironmentOverrides(true)
                  .build();
          default ->
              throw new IllegalStateException(
                  "Expect app.auth.type to be one of %s, but was '%s'"
                      .formatted(AuthType.values(), config.getAuth().getType()));
        };

    return builder.credentialsProvider(credentialsProvider);
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
