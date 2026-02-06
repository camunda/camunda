/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static io.camunda.zeebe.test.util.testcontainers.TestSearchContainers.*;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaRdbmsInvocationContextProviderExtension
    implements TestTemplateInvocationContextProvider, BeforeAllCallback, AutoCloseable {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaRdbmsInvocationContextProviderExtension.class);

  private static final Map<String, CamundaRdbmsTestApplication> SUPPORTED_TEST_APPLICATIONS;

  static {
    final Map<String, CamundaRdbmsTestApplication> applications = new java.util.HashMap<>();
    applications.put(
        "camundaWithH2",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class).withRdbms().withH2());
    applications.put(
        "camundaWithPostgresSQL",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withDatabaseContainer(createDefaultPostgresContainer()));
    applications.put(
        "camundaWithManualPostgresSQL",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withUnifiedConfig(c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
            .withDatabaseContainer(createManualPostgresContainer()));
    applications.put(
        "camundaWithMariaDB",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withDatabaseContainer(createDefaultMariaDBContainer()));
    applications.put(
        "camundaWithManualMariaDB",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withUnifiedConfig(c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
            .withDatabaseContainer(createManualMariaDBContainer()));
    applications.put(
        "camundaWithMySQL",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withDatabaseContainer(createDefaultMySQLContainer()));
    applications.put(
        "camundaWithManualMySQL",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withUnifiedConfig(c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
            .withDatabaseContainer(createManualMySQLContainer()));
    applications.put(
        "camundaWithOracleDB",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withDatabaseContainer(createDefaultOracleContainer()));
    applications.put(
        "camundaWithManualOracleDB",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withUnifiedConfig(c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
            .withDatabaseContainer(createManualOracleContainer()));
    applications.put(
        "camundaWithMssqlDB",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withUnifiedConfig(
                c -> {
                  c.getData().getSecondaryStorage().getRdbms().setUsername("sa");
                })
            .withDatabaseContainer(createDefaultMSSQLServerContainer()));
    applications.put(
        "camundaWithManualMssqlDB",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withRdbms()
            .withUnifiedConfig(c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
            .withDatabaseContainer(createManualMSSQLServerContainer()));
    SUPPORTED_TEST_APPLICATIONS = Map.copyOf(applications);
  }

  private final Set<String> useTestApplications;

  /** By default, only all default test applications will be used */
  public CamundaRdbmsInvocationContextProviderExtension() {
    useTestApplications =
        Set.of(
            "camundaWithH2",
            "camundaWithPostgresSQL",
            "camundaWithMariaDB",
            "camundaWithMySQL",
            "camundaWithOracleDB",
            "camundaWithMssqlDB");
  }

  /**
   * This can be used to configure which test applications should be used:
   *
   * <pre>{@code
   * @RegisterExtension
   * static final CamundaRdbmsInvocationContextProviderExtension testApplication =
   *     new CamundaRdbmsInvocationContextProviderExtension("camundaWithH2");
   * }</pre>
   *
   * This can make sense for e.g. development phase where you want to run only a subset of the
   * databases
   *
   * @param useTestApplications the test applications to use
   */
  public CamundaRdbmsInvocationContextProviderExtension(final String... useTestApplications) {
    this.useTestApplications = Set.of(useTestApplications);
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    return useTestApplications.stream().map(this::invocationContext);
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    useTestApplications.forEach(
        key -> {
          final CamundaRdbmsTestApplication testApplication = SUPPORTED_TEST_APPLICATIONS.get(key);
          if (!testApplication.isStarted()) {
            LOGGER.info("Start up CamundaDatabaseTestApplication '{}'...", key);
            testApplication.start();
            LOGGER.info("Start up of CamundaDatabaseTestApplication '{}' finished.", key);
          }
        });

    // Your "before all tests" startup logic goes here
    // The following line registers a callback hook when the root test context is shut down
    final String key = "RDBMS DB - Multiple Database Tests";
    context.getRoot().getStore(GLOBAL).put(key, this);
  }

  private TestTemplateInvocationContext invocationContext(final String standaloneCamundaKey) {
    return new TestTemplateInvocationContext() {

      @Override
      public String getDisplayName(final int invocationIndex) {
        return standaloneCamundaKey;
      }

      @Override
      public List<Extension> getAdditionalExtensions() {

        return List.of(
            new CamundaDatabaseTestApplicationResolver(
                standaloneCamundaKey, SUPPORTED_TEST_APPLICATIONS.get(standaloneCamundaKey)));
      }
    };
  }

  @Override
  public void close() {
    LOGGER.info("Resource closed - Close CamundaRdbmsInvocationContextProviderExtension");
  }
}
