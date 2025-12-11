/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.google.common.util.concurrent.UncheckedExecutionException;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.benchmark.MetricsReader;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.AppConfigLoader;
import io.camunda.zeebe.config.AuthCfg.AuthType;
import io.camunda.zeebe.util.FileUtil;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class App implements Runnable {
  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(App.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(App.class);

  protected final AppCfg config;
  protected PrometheusMeterRegistry registry;
  protected ClientInterceptor monitoringInterceptor;

  private HTTPServer monitoringServer;
  private final Path credentialsCachePath;

  protected App(final AppCfg config) {
    this.config = config;

    Path credentialsCachePath = null;
    try {
      credentialsCachePath = Files.createTempDirectory(".camunda").resolve("credentials.json");
    } catch (final IOException e) {
      LOG.warn(
          """
          Failed to create credentials cache directory; there will be no credentials cache, and \
          you may run into rate limiting issues with your IdP""",
          e);
    }

    this.credentialsCachePath = credentialsCachePath;
  }

  static void createApp(final Function<AppCfg, App> appFactory) {
    final AppCfg appCfg = AppConfigLoader.load();

    final var app = appFactory.apply(appCfg);
    Runtime.getRuntime().addShutdownHook(new Thread(app::onShutdown));
    app.startMonitoringServer();
    app.run();
  }

  private void startMonitoringServer() {
    registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    registry.config().meterFilter(new PrometheusRenameFilter());

    try {
      // you can set the daemon flag to false if you want the server to block
      monitoringServer =
          HTTPServer.builder()
              .port(config.getMonitoringPort())
              .registry(registry.getPrometheusRegistry())
              .buildAndStart();
    } catch (final IOException e) {
      LOG.error("Problem on starting monitoring server.", e);
    }

    monitoringInterceptor = new MetricCollectingClientInterceptor(registry);
    registerDefaultInstrumentation();
  }

  @SuppressWarnings("resource") // closeable metrics will be closed when the registry is closed
  private void registerDefaultInstrumentation() {
    new ClassLoaderMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    new JvmGcMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
  }

  private void onShutdown() {
    if (monitoringServer != null) {
      monitoringServer.stop();
      monitoringServer = null;
    }

    if (credentialsCachePath != null) {
      try {
        FileUtil.deleteFolderIfExists(credentialsCachePath.getParent());
      } catch (final IOException e) {
        LOG.debug("Failed to delete credentials cache directory", e);
      }
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
                  LOG.info("Broker {} - {} ({})", b.getNodeId(), b.getAddress(), b.getVersion());
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
        reportErrorAndSleep("Failed to retrieve topology due to client exception: ", e);
      } catch (final Exception e) {
        reportErrorAndSleep("Topology request failed: ", e);
      }
    }
  }

  private void reportErrorAndSleep(final String error, final Exception e) {
    THROTTLED_LOGGER.warn(error, e);
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException ex) {
      throw new RuntimeException(ex);
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

    final var auth = config.getAuth();
    final var credentialsProvider =
        switch (auth.getType()) {
          case NONE -> new NoopCredentialsProvider();
          case BASIC ->
              CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                  .username(auth.getBasic().getUsername())
                  .password(auth.getBasic().getPassword())
                  .applyEnvironmentOverrides(true)
                  .build();
          case OAUTH -> {
            final var cachePath =
                credentialsCachePath != null
                    ? credentialsCachePath.toAbsolutePath().toString()
                    : null;

            yield CredentialsProvider.newCredentialsProviderBuilder()
                .clientId(auth.getOauth().getClientId())
                .clientSecret(auth.getOauth().getClientSecret())
                .audience(auth.getOauth().getAudience())
                .authorizationServerUrl(auth.getOauth().getAuthzUrl())
                .credentialsCachePath(cachePath)
                .applyEnvironmentOverrides(true)
                .build();
          }
          default ->
              throw new IllegalStateException(
                  "Expect app.auth.type to be one of %s, but was '%s'"
                      .formatted(AuthType.values(), auth.getType()));
        };

    return builder.credentialsProvider(credentialsProvider);
  }

  protected MetricsReader createMetricsReader() {
    return new MetricsReader(config.getBrokerManagementUrl());
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
