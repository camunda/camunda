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
import io.camunda.it.utils.ZeebeClientTestFactory.Authenticated;
import io.camunda.it.utils.ZeebeClientTestFactory.User;
import io.camunda.zeebe.client.ZeebeClient;
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
 */
public class BrokerWithCamundaExporterITInvocationProvider
    implements TestTemplateInvocationContextProvider, AfterAllCallback, BeforeAllCallback {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BrokerWithCamundaExporterITInvocationProvider.class);

  private final Map<String, ExporterType> exporterTypes;
  private final Map<String, TestStandaloneBroker> testBrokers = new HashMap<>();
  private final Set<Profile> additionalProfiles = new HashSet<>();
  private Consumer<BrokerBasedProperties> additionalBrokerConfig = cfg -> {};
  private final Map<String, Object> additionalProperties = new HashMap<>();
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final Map<String, ZeebeClientTestFactory> zeebeClientTestFactories = new HashMap<>();
  private final List<User> registeredUsers = new ArrayList<>();

  public BrokerWithCamundaExporterITInvocationProvider() {
    exporterTypes = new HashMap<>();
    exporterTypes.put(
        "with-camunda-exporter-elasticsearch", ExporterType.CAMUNDA_EXPORTER_ELASTIC_SEARCH);
  }

  public BrokerWithCamundaExporterITInvocationProvider withAdditionalProfiles(
      final Profile... profiles) {
    additionalProfiles.addAll(asList(profiles));
    return this;
  }

  public BrokerWithCamundaExporterITInvocationProvider withAdditionalProperty(
      final String key, final Object value) {
    additionalProperties.put(key, value);
    return this;
  }

  public BrokerWithCamundaExporterITInvocationProvider withAdditionalBrokerConfig(
      final Consumer<BrokerBasedProperties> modifier) {
    additionalBrokerConfig = additionalBrokerConfig.andThen(modifier);
    return this;
  }

  public BrokerWithCamundaExporterITInvocationProvider withAuthorizationsEnabled() {
    return withAdditionalBrokerConfig(
            cfg ->
                cfg.getExperimental().getEngine().getAuthorizations().setEnableAuthorization(true))
        .withAdditionalProperty("camunda.security.authorizations.enabled", true);
  }

  public BrokerWithCamundaExporterITInvocationProvider withUsers(final User... users) {
    registeredUsers.addAll(List.of(users));
    return this;
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
                      TestSearchContainers.createDefeaultElasticsearchContainer();
                  elasticsearchContainer.start();
                  closeables.add(elasticsearchContainer);

                  final var testBroker =
                      new TestStandaloneBroker()
                          .withCamundaExporter(
                              "http://" + elasticsearchContainer.getHttpHostAddress())
                          .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
                          .withBrokerConfig(additionalBrokerConfig)
                          .withRecordingExporter(true)
                          .withProperty("camunda.rest.query.enabled", true)
                          .withProperty(
                              "camunda.database.url",
                              "http://" + elasticsearchContainer.getHttpHostAddress())
                          .withAdditionalProperties(additionalProperties)
                          .withAdditionalProfiles(additionalProfiles)
                          .start();
                  closeables.add(testBroker);
                  testBrokers.put(entry.getKey(), testBroker);
                  testBroker.awaitCompleteTopology();
                  final var zeebeClientTestFactory =
                      new ZeebeClientTestFactory().withUsers(registeredUsers);
                  zeebeClientTestFactories.put(entry.getKey(), zeebeClientTestFactory);
                  closeables.add(zeebeClientTestFactory);
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
                return Set.of(TestStandaloneBroker.class, ZeebeClient.class)
                    .contains(parameterCtx.getParameter().getType());
              }

              @Override
              public Object resolveParameter(
                  final ParameterContext parameterCtx, final ExtensionContext extensionCtx) {
                final Parameter parameter = parameterCtx.getParameter();
                final TestGateway<?> testGateway = testBrokers.get(standaloneCamundaKey);
                if (TestStandaloneBroker.class.equals(parameter.getType())) {
                  return testGateway;
                } else if (ZeebeClient.class.equals(parameter.getType())) {
                  final var zeebeClientTestFactory =
                      zeebeClientTestFactories.get(standaloneCamundaKey);
                  return zeebeClientTestFactory.createZeebeClient(
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

  public enum ExporterType {
    CAMUNDA_EXPORTER_ELASTIC_SEARCH
  }
}
