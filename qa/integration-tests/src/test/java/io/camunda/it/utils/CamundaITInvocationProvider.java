/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import io.camunda.application.Profile;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.client.CamundaClient;
import io.camunda.it.utils.CamundaClientTestFactory.Authenticated;
import io.camunda.it.utils.CamundaClientTestFactory.User;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.ModifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invocation context provider that provides TestStandaloneBroker instances configured with each
 * exporter type. The lifecyle of the TestStandaloneBroker and the exporter backend is per class.
 * The tests must take this into account, as the state in Zeebe and the exporter backend is not
 * reset between test cases.
 */
public class CamundaITInvocationProvider
    implements TestTemplateInvocationContextProvider,
        AfterAllCallback,
        BeforeAllCallback,
        ParameterResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaITInvocationProvider.class);

  private final Set<ExporterType> supportedExporterTypes = new HashSet<>();
  private final ExporterType activeExporterType;
  private final Map<ExporterType, TestStandaloneBroker> testBrokers = new HashMap<>();
  private final Set<Profile> additionalProfiles = new HashSet<>();
  private final Consumer<BrokerBasedProperties> additionalBrokerConfig = cfg -> {};
  private final Consumer<CamundaSecurityProperties> additionalSecurityConfig =
      cfg -> {
        cfg.getInitialization()
            .getUsers()
            .add(
                new ConfiguredUser(
                    InitializationConfiguration.DEFAULT_USER_USERNAME,
                    InitializationConfiguration.DEFAULT_USER_PASSWORD,
                    InitializationConfiguration.DEFAULT_USER_NAME,
                    InitializationConfiguration.DEFAULT_USER_EMAIL));
      };
  private final Map<String, Object> additionalProperties = new HashMap<>();
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final Map<ExporterType, CamundaClientTestFactory> camundaClientTestFactories =
      new HashMap<>();
  private final List<User> registeredUsers = new ArrayList<>();
  private final String esUrl;

  public CamundaITInvocationProvider() {
    supportedExporterTypes.add(ExporterType.CAMUNDA_EXPORTER_ELASTIC_SEARCH);
    supportedExporterTypes.add(ExporterType.RDBMS_EXPORTER_H2);

    // TODO RESOLVE / DETECT SOMEHOW the secondary storage to be used!
    activeExporterType = ExporterType.CAMUNDA_EXPORTER_ELASTIC_SEARCH;

    // defaults
    esUrl = "http://localhost:9200";
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    LOGGER.info("Starting up '{}' camunda instances", supportedExporterTypes.size());
    final Class<?> testClass = context.getRequiredTestClass();
    supportedExporterTypes.parallelStream()
        .forEach(
            exporterType -> {
              if (exporterType != activeExporterType) {
                return;
              }
              LOGGER.info("Start up '{}'", exporterType);
              switch (exporterType) {
                case CAMUNDA_EXPORTER_ELASTIC_SEARCH -> {
                  final var testBroker =
                      new TestStandaloneBroker()

                          // TODO set prefix configuration based on TEST class
                          .withCamundaExporter(esUrl)
                          .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
                          .withBrokerConfig(additionalBrokerConfig)
                          .withSecurityConfig(additionalSecurityConfig)
                          .withRecordingExporter(true)
                          .withProperty("camunda.database.url", esUrl)
                          .withAdditionalProperties(additionalProperties)
                          .withAdditionalProfiles(additionalProfiles)
                          .start();
                  closeables.add(testBroker);
                  testBrokers.put(exporterType, testBroker);
                  testBroker.awaitCompleteTopology();
                  final var camundaClientTestFactory =
                      new CamundaClientTestFactory().withUsers(registeredUsers);
                  camundaClientTestFactories.put(exporterType, camundaClientTestFactory);
                  closeables.add(camundaClientTestFactory);
                  addClientFactory(exporterType);
                }
                case RDBMS_EXPORTER_H2 -> {
                  final var testBroker =
                      new TestStandaloneBroker()
                          .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
                          .withBrokerConfig(additionalBrokerConfig)
                          .withRecordingExporter(true)
                          .withRdbmsExporter()
                          .withAdditionalProperties(additionalProperties)
                          .withAdditionalProfiles(additionalProfiles)
                          .start();
                  closeables.add(testBroker);
                  testBrokers.put(exporterType, testBroker);
                  testBroker.awaitCompleteTopology();
                  addClientFactory(exporterType);
                }
                default -> throw new RuntimeException("Unknown exporter type");
              }
              LOGGER.info("Start up of '{}' finished.", exporterType);
            });

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
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    return Stream.of(invocationContext(activeExporterType));
  }

  private TestTemplateInvocationContext invocationContext(final ExporterType exporterType) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return "Exporter: " + exporterType.name();
      }

      @Override
      public List<Extension> getAdditionalExtensions() {
        return List.of(
            new ParameterResolver() {

              @Override
              public boolean supportsParameter(
                  final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
                return Set.of(TestStandaloneBroker.class, CamundaClient.class)
                    .contains(parameterCtx.getParameter().getType());
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
                final Parameter parameter = parameterCtx.getParameter();
                final TestGateway<?> testGateway = testBrokers.get(exporterType);
                if (TestStandaloneBroker.class.equals(parameter.getType())) {
                  return testGateway;
                } else if (CamundaClient.class.equals(parameter.getType())) {
                  final var camundaClientTestFactory = camundaClientTestFactories.get(exporterType);
                  return camundaClientTestFactory.createCamundaClient(
                      testGateway, parameter.getAnnotation(Authenticated.class));
                }
                throw new IllegalArgumentException(
                    "Unsupported parameter type:" + parameter.getType());
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
    final var camundaClientTestFactory = camundaClientTestFactories.get(activeExporterType);
    final TestGateway<?> testGateway = testBrokers.get(activeExporterType);
    return camundaClientTestFactory.createCamundaClient(testGateway, null);
  }

  private void addClientFactory(final ExporterType exporterType) {
    final var camundaClientTestFactory = new CamundaClientTestFactory().withUsers(registeredUsers);
    camundaClientTestFactories.put(exporterType, camundaClientTestFactory);
    closeables.add(camundaClientTestFactory);
  }

  public enum ExporterType {
    CAMUNDA_EXPORTER_ELASTIC_SEARCH,
    RDBMS_EXPORTER_H2
  }
}
