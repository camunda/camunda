/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import static java.util.Arrays.asList;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Invocation context provider that provides TestStandaloneBroker instances configured with each
 * exporter type. The lifecyle of the TestStandaloneBroker and the exporter backend is per class.
 * The tests must take this into account, as the state in Zeebe and the exporter backend is not
 * reset between test cases.
 */
public class CamundaExporterITInvocationProvider
    implements TestTemplateInvocationContextProvider, AfterAllCallback, BeforeAllCallback {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaExporterITInvocationProvider.class);

  private static final DockerImageName ELASTIC_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
          .withTag(RestClient.class.getPackage().getImplementationVersion());

  private final Map<String, ExporterType> exporterTypes;
  private final Map<String, TestStandaloneBroker> testBrokers = new HashMap<>();

  private final List<AutoCloseable> closeables = new ArrayList<>();

  public CamundaExporterITInvocationProvider() {
    exporterTypes = new HashMap<>();
    exporterTypes.put(
        "with-camunda-exporter-elasticsearch", ExporterType.CAMUNDA_EXPORTER_ELASTIC_SEARCH);
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    LOGGER.info("Starting up '{}' camunda instances", exporterTypes.size());
    exporterTypes.entrySet().parallelStream()
        .forEach(
            entry -> {
              LOGGER.info("Start up '{}'", entry.getKey());

              switch (entry.getValue()) {
                case CAMUNDA_EXPORTER_ELASTIC_SEARCH -> {
                  final ElasticsearchContainer elasticsearchContainer =
                      new ElasticsearchContainer(ELASTIC_IMAGE)
                          // use JVM option files to avoid overwriting default options set by the ES
                          // container class
                          .withClasspathResourceMapping(
                              "elasticsearch-fast-startup.options",
                              "/usr/share/elasticsearch/config/jvm.options.d/ elasticsearch-fast-startup.options",
                              BindMode.READ_ONLY)
                          // can be slow in CI
                          .withStartupTimeout(Duration.ofMinutes(5))
                          .withEnv("action.auto_create_index", "true")
                          .withEnv("xpack.security.enabled", "false")
                          .withEnv("xpack.watcher.enabled", "false")
                          .withEnv("xpack.ml.enabled", "false");
                  elasticsearchContainer.start();
                  closeables.add(elasticsearchContainer);

                  final var testBroker =
                      new TestStandaloneBroker()
                          .withCamundaExporter(
                              "http://" + elasticsearchContainer.getHttpHostAddress())
                          .withRecordingExporter(true)
                          .withProperty("zeebe.broker.gateway.enable", true)
                          .withProperty("camunda.rest.query.enabled", true)
                          .withProperty(
                              "camunda.database.url",
                              "http://" + elasticsearchContainer.getHttpHostAddress())
                          .start();
                  closeables.add(testBroker);
                  testBrokers.put(entry.getKey(), testBroker);
                  testBroker.awaitCompleteTopology();
                }
                default -> throw new RuntimeException("Unknown exporter type");
              }
              LOGGER.info("Start up of '{}' finished.", entry.getKey());
            });
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    return testBrokers.keySet().stream().map(this::invocationContext);
  }

  private TestTemplateInvocationContext invocationContext(final String standaloneCamundaKey) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return standaloneCamundaKey;
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return asList(
            new ParameterResolver() {

              @Override
              public boolean supportsParameter(
                  final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
                return parameterCtx.getParameter().getType().equals(TestStandaloneBroker.class);
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
                return testBrokers.get(standaloneCamundaKey);
              }
            });
      }
    };
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    closeables.parallelStream()
        .forEach(
            c -> {
              try {
                c.close();
              } catch (final Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  public enum ExporterType {
    CAMUNDA_EXPORTER_ELASTIC_SEARCH
  }
}
