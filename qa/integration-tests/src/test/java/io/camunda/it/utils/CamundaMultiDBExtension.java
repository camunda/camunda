/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.NewCamundaTestApplication;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
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
public class CamundaMultiDBExtension
    implements AfterAllCallback, BeforeAllCallback, ParameterResolver {

  public static final String PROP_CAMUNDA_IT_DATABASE_TYPE = "camunda.it.database.type";
  public static final String DEFAULT_ES_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_ADMIN_USER = "admin";
  public static final String DEFAULT_OS_ADMIN_PW = "yourStrongPassword123!";

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaMultiDBExtension.class);
  private final DatabaseType databaseType;
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final NewCamundaTestApplication testApplication;
  private String testPrefix;

  public CamundaMultiDBExtension() {
    this(new NewCamundaTestApplication());

    closeables.add(testApplication);
    testApplication
        .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
        .withRecordingExporter(true);
  }

  public CamundaMultiDBExtension(final NewCamundaTestApplication testApplication) {
    this.testApplication = testApplication;
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
      case ES -> testApplication.withElasticsearchSupport(DEFAULT_ES_URL, testPrefix);
      case OS ->
          testApplication.withOpensearchSupport(
              DEFAULT_OS_URL, testPrefix, DEFAULT_OS_ADMIN_USER, DEFAULT_OS_ADMIN_PW);
      case RDBMS -> testApplication.withRdbmsExporter();
      default -> throw new RuntimeException("Unknown exporter type");
    }
    testApplication.start();
    testApplication.awaitCompleteTopology();

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
