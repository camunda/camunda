/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import static java.util.Arrays.asList;

import io.camunda.application.Profile;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.multidb.CamundaClientTestFactory;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Invocation context provider that provides TestStandaloneBroker instances configured with each
 * exporter type. The lifecyle of the TestStandaloneBroker and the exporter backend is per class.
 * The tests must take this into account, as the state in Zeebe and the exporter backend is not
 * reset between test cases.
 *
 * @deprecated Make use of {@link io.camunda.qa.util.multidb.MultiDbTest}, and {@link
 *     io.camunda.qa.util.multidb.CamundaMultiDBExtension}
 */
@Deprecated(forRemoval = true)
public class BrokerITInvocationProvider
    implements TestTemplateInvocationContextProvider, AfterAllCallback, BeforeAllCallback {
  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerITInvocationProvider.class);

  private final Set<ExporterType> supportedExporterTypes = new HashSet<>();
  private final Set<ExporterType> activeExporterTypes = new HashSet<>();
  private final Map<ExporterType, TestStandaloneBroker> testBrokers = new HashMap<>();
  private final Set<Profile> additionalProfiles = new HashSet<>();
  private Consumer<BrokerBasedProperties> additionalBrokerConfig = cfg -> {};
  private Consumer<CamundaSecurityProperties> additionalSecurityConfig =
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

  public BrokerITInvocationProvider() {
    supportedExporterTypes.add(ExporterType.CAMUNDA_EXPORTER_ELASTIC_SEARCH);
    supportedExporterTypes.add(ExporterType.RDBMS_EXPORTER_H2);

    // Default
    activeExporterTypes.addAll(supportedExporterTypes);
  }

  /** Without Camunda Exporter (ES/OS) */
  public BrokerITInvocationProvider withoutCamundaExporter() {
    activeExporterTypes.remove(ExporterType.CAMUNDA_EXPORTER_ELASTIC_SEARCH);
    return this;
  }

  /** Without Rdbms Exporter (H2) */
  public BrokerITInvocationProvider withoutRdbmsExporter() {
    activeExporterTypes.remove(ExporterType.RDBMS_EXPORTER_H2);
    return this;
  }

  public BrokerITInvocationProvider withAdditionalProfiles(final Profile... profiles) {
    additionalProfiles.addAll(asList(profiles));
    return this;
  }

  public BrokerITInvocationProvider withAdditionalProperty(final String key, final Object value) {
    additionalProperties.put(key, value);
    return this;
  }

  public BrokerITInvocationProvider withAdditionalBrokerConfig(
      final Consumer<BrokerBasedProperties> modifier) {
    additionalBrokerConfig = additionalBrokerConfig.andThen(modifier);
    return this;
  }

  public BrokerITInvocationProvider withAdditionalSecurityConfig(
      final Consumer<CamundaSecurityProperties> modifier) {
    additionalSecurityConfig = additionalSecurityConfig.andThen(modifier);
    return this;
  }

  public BrokerITInvocationProvider withBasicAuth() {
    withAdditionalProperty(AuthenticationProperties.METHOD, AuthenticationMethod.BASIC.name());
    withAdditionalProfiles(Profile.CONSOLIDATED_AUTH);
    return this;
  }

  public BrokerITInvocationProvider withAuthorizationsEnabled() {
    return withAdditionalSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));
  }

  public BrokerITInvocationProvider withUnprotectedApi() {
    return withAdditionalSecurityConfig(cfg -> cfg.getAuthentication().setUnprotectedApi(true));
  }

  public BrokerITInvocationProvider withUsers(final User... users) {
    registeredUsers.addAll(List.of(users));
    return this;
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    LOGGER.info("Starting up '{}' camunda instances", activeExporterTypes.size());
    activeExporterTypes.parallelStream()
        .forEach(
            exporterType -> {
              LOGGER.info("Start up '{}'", exporterType);

              switch (exporterType) {
                case CAMUNDA_EXPORTER_ELASTIC_SEARCH -> {
                  final ElasticsearchContainer elasticsearchContainer =
                      TestSearchContainers.createDefeaultElasticsearchContainer();
                  elasticsearchContainer.start();
                  closeables.add(elasticsearchContainer);

                  final var testBroker =
                      new TestStandaloneBroker()
                          .withCamundaExporter(
                              "http://" + elasticsearchContainer.getHttpHostAddress())
                          .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
                          .withBrokerConfig(additionalBrokerConfig)
                          .withSecurityConfig(additionalSecurityConfig)
                          .withRecordingExporter(true)
                          .withProperty(
                              "camunda.database.url",
                              "http://" + elasticsearchContainer.getHttpHostAddress())
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
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    return activeExporterTypes.stream().map(this::invocationContext);
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
