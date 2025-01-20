/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import io.camunda.client.CamundaClient;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.ModifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** TBD */
public class CamundaExternalDBExtension
    implements AfterAllCallback, BeforeAllCallback, ParameterResolver {

  public static final String PROP_CAMUNDA_IT_DATABASE_TYPE = "camunda.it.database.type";
  public static final String DEFAULT_ES_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_ADMIN_USER = "admin";
  public static final String DEFAULT_OS_ADMIN_PW = "yourStrongPassword123!";

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaExternalDBExtension.class);
  private final DatabaseType databaseType;
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final TestStandaloneApplication<?> testApplication;
  private String testPrefix;
  private final OperateProperties operateProperties;
  private final TasklistProperties tasklistProperties;

  public CamundaExternalDBExtension() {
    this(new NewCamundaTestApplication());

    closeables.add(testApplication);
    testApplication
        .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
        .withRecordingExporter(true);
  }

  public CamundaExternalDBExtension(final TestStandaloneApplication testApplication) {
    this.testApplication = testApplication;

    // we are configuring this always, even if we might not use the applications
    operateProperties = new OperateProperties();
    tasklistProperties = new TasklistProperties();
    // resolve active database and exporter type
    final String property = System.getProperty(PROP_CAMUNDA_IT_DATABASE_TYPE);
    databaseType =
        property == null ? DatabaseType.ES : DatabaseType.valueOf(property.toUpperCase());
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    LOGGER.info("Starting up Camunda instance, with {}", databaseType);
    final Class<?> testClass = context.getRequiredTestClass();
    testPrefix = testClass.getSimpleName().toLowerCase();

    switch (databaseType) {
      case ES -> {
        operateProperties.getElasticsearch().setUrl(DEFAULT_ES_URL);
        operateProperties.getElasticsearch().setIndexPrefix(testPrefix);
        operateProperties.getZeebeElasticsearch().setUrl(DEFAULT_ES_URL);
        tasklistProperties.getElasticsearch().setUrl(DEFAULT_ES_URL);
        tasklistProperties.getElasticsearch().setIndexPrefix(testPrefix);
        tasklistProperties.getZeebeElasticsearch().setUrl(DEFAULT_ES_URL);

        testApplication.withExporter(
            "CamundaExporter",
            cfg -> {
              cfg.setClassName(CamundaExporter.class.getName());
              cfg.setArgs(
                  Map.of(
                      "connect",
                      Map.of(
                          "url",
                          DEFAULT_ES_URL,
                          "indexPrefix",
                          testPrefix,
                          "type",
                          io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH),
                      "index",
                      Map.of("prefix", testPrefix),
                      "bulk",
                      Map.of("size", 1)));
            });

        testApplication.withProperty(
            "camunda.database.type",
            io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH);
        testApplication.withProperty("camunda.database.indexPrefix", testPrefix);
      }
      case OS -> {
        final OperateOpensearchProperties operateOpensearch = operateProperties.getOpensearch();
        operateOpensearch.setUrl(DEFAULT_OS_URL);
        operateOpensearch.setIndexPrefix(testPrefix);
        operateOpensearch.setPassword(DEFAULT_OS_ADMIN_PW);
        operateOpensearch.setUsername(DEFAULT_OS_ADMIN_USER);

        tasklistProperties.setDatabase("opensearch");
        final TasklistOpenSearchProperties tasklistOpensearch = tasklistProperties.getOpenSearch();
        tasklistOpensearch.setUrl(DEFAULT_OS_URL);
        tasklistOpensearch.setIndexPrefix(testPrefix);
        tasklistOpensearch.setPassword(DEFAULT_OS_ADMIN_PW);
        tasklistOpensearch.setUsername(DEFAULT_OS_ADMIN_USER);

        testApplication.withExporter(
            "CamundaExporter",
            cfg -> {
              cfg.setClassName(CamundaExporter.class.getName());
              cfg.setArgs(
                  Map.of(
                      "connect",
                      Map.of(
                          "url",
                          DEFAULT_OS_URL,
                          "indexPrefix",
                          testPrefix,
                          "type",
                          io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH,
                          "username",
                          DEFAULT_OS_ADMIN_USER,
                          "password",
                          DEFAULT_OS_ADMIN_PW),
                      "index",
                      Map.of("prefix", testPrefix),
                      "bulk",
                      Map.of("size", 1)));
            });

        testApplication.withProperty(
            "camunda.database.type",
            io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH);
        testApplication.withProperty("camunda.operate.database", "opensearch");
        testApplication.withProperty("camunda.tasklist.database", "opensearch");
        testApplication.withProperty("camunda.database.indexPrefix", testPrefix);
        testApplication.withProperty("camunda.database.username", DEFAULT_OS_ADMIN_USER);
        testApplication.withProperty("camunda.database.password", DEFAULT_OS_ADMIN_PW);
      }
      case RDBMS -> {
        // do nothing for now
      }
      default -> throw new RuntimeException("Unknown exporter type");
    }
    testApplication.start();

    try (final var client = createCamundaClient()) {
      Awaitility.await("until cluster topology is complete")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                      .isComplete(1, 1, 1));
    }

    injectFields(testClass, null, ModifierSupport::isStatic);
  }

  private void injectFields(
      final Class<?> testClass, final Object testInstance, Predicate<Field> predicate) {
    predicate = predicate.and(field -> field.getType() == CamundaClient.class);
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (predicate.test(field)) {
          field.setAccessible(true);
          field.set(testInstance, createCamundaClient());
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    if (databaseType == DatabaseType.ES || databaseType == DatabaseType.OS) {
      final URI deleteEndpoint = URI.create(String.format("%s/%s*", DEFAULT_ES_URL, testPrefix));
      final HttpRequest httpRequest = HttpRequest.newBuilder().DELETE().uri(deleteEndpoint).build();
      try (final HttpClient httpClient = HttpClient.newHttpClient()) {
        final HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
        final int statusCode = response.statusCode();
        if (statusCode / 100 == 2) {
          LOGGER.info("Test data deleted.");
        } else {
          LOGGER.warn("Failure on deleting test data. Status code: {}", statusCode);
        }
      } catch (final IOException | InterruptedException e) {
        LOGGER.warn("Failure on deleting test data.", e);
      }
    }

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

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == CamundaClient.class;
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return createCamundaClient();
  }

  private CamundaClient createCamundaClient() {
    return testApplication.newClientBuilder().build();
  }

  public enum DatabaseType {
    ES,
    OS,
    RDBMS
  }
}
