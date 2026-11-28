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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaRdbmsInvocationContextProviderExtension
    implements TestTemplateInvocationContextProvider, BeforeAllCallback, AfterAllCallback {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaRdbmsInvocationContextProviderExtension.class);

  private static final Map<String, CamundaRdbmsTestApplication> SUPPORTED_TEST_APPLICATIONS;

  static {
    SUPPORTED_TEST_APPLICATIONS =
        allTestApplications().stream()
            .collect(
                Collectors.toUnmodifiableMap(key -> key, key -> createTestApplication(key, false)));
  }

  private final Set<String> useTestApplications;
  private final Map<String, CamundaRdbmsTestApplication> testApplications;
  private final boolean shared;

  /** By default, only all default test applications will be used */
  public CamundaRdbmsInvocationContextProviderExtension() {
    this(defaultTestApplications());
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
    this(Set.of(useTestApplications));
  }

  private CamundaRdbmsInvocationContextProviderExtension(final Set<String> useTestApplications) {
    this(useTestApplications, SUPPORTED_TEST_APPLICATIONS, true);
  }

  private CamundaRdbmsInvocationContextProviderExtension(
      final Set<String> useTestApplications,
      final Map<String, CamundaRdbmsTestApplication> testApplications,
      final boolean shared) {
    this.useTestApplications = useTestApplications;
    this.testApplications = testApplications;
    this.shared = shared;
  }

  public static CamundaRdbmsInvocationContextProviderExtension isolated() {
    final var keys = defaultTestApplications();
    return new CamundaRdbmsInvocationContextProviderExtension(
        keys,
        keys.stream()
            .collect(
                Collectors.toUnmodifiableMap(key -> key, key -> createTestApplication(key, true))),
        false);
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
    try {
      useTestApplications.forEach(
          key -> {
            final CamundaRdbmsTestApplication testApplication = testApplications.get(key);
            if (!testApplication.isStarted()) {
              LOGGER.info("Start up CamundaDatabaseTestApplication '{}'...", key);
              testApplication.start();
              LOGGER.info("Start up of CamundaDatabaseTestApplication '{}' finished.", key);
            }
          });
    } catch (final RuntimeException | Error e) {
      closeIsolatedApplications();
      throw e;
    }

    // Your "before all tests" startup logic goes here
    // The following line registers a callback hook when the root test context is shut down
    if (shared) {
      final String key = "RDBMS DB - Multiple Database Tests";
      context.getRoot().getStore(GLOBAL).put(key, this);
    }
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
                standaloneCamundaKey, testApplications.get(standaloneCamundaKey), shared));
      }
    };
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    closeIsolatedApplications();
  }

  private void closeIsolatedApplications() {
    if (!shared) {
      useTestApplications.forEach(
          key -> {
            try {
              testApplications.get(key).close();
            } catch (final Exception e) {
              LOGGER.warn("Failed to close isolated RDBMS test application '{}'.", key, e);
            }
          });
    }
  }

  private static Set<String> defaultTestApplications() {
    return Set.of(
        "camundaWithH2",
        "camundaWithPostgresSQL",
        "camundaWithMariaDB",
        "camundaWithMySQL",
        "camundaWithOracleDB",
        "camundaWithMssqlDB");
  }

  private static Set<String> allTestApplications() {
    return Set.of(
        "camundaWithH2",
        "camundaWithPostgresSQL",
        "camundaWithPostgresReplicationCluster",
        "camundaWithManualPostgresSQL",
        "camundaWithMariaDB",
        "camundaWithManualMariaDB",
        "camundaWithMySQL",
        "camundaWithManualMySQL",
        "camundaWithOracleDB",
        "camundaWithManualOracleDB",
        "camundaWithMssqlDB",
        "camundaWithMssqlReplicationCluster",
        "camundaWithManualMssqlDB");
  }

  private static CamundaRdbmsTestApplication createTestApplication(
      final String key, final boolean isolated) {
    return switch (key) {
      case "camundaWithH2" ->
          isolated
              ? createCamundaRdbmsTestApplication().withIsolatedH2("isolated-" + UUID.randomUUID())
              : createCamundaRdbmsTestApplication().withH2();
      case "camundaWithPostgresSQL" ->
          createCamundaRdbmsTestApplication()
              .withDatabaseContainer(createDefaultPostgresContainer());
      case "camundaWithPostgresReplicationCluster" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> {
                    final var rdbms = c.getData().getSecondaryStorage().getRdbms();
                    rdbms.getAsyncReplication().setEnabled(true);
                    rdbms.getAsyncReplication().setMinSyncReplicas(1);
                  })
              .withDatabaseContainer(new PostgresReplicationClusterContainer());
      case "camundaWithManualPostgresSQL" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
              .withDatabaseContainer(createManualPostgresContainer());
      case "camundaWithMariaDB" ->
          createCamundaRdbmsTestApplication()
              .withDatabaseContainer(createDefaultMariaDBContainer());
      case "camundaWithManualMariaDB" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
              .withDatabaseContainer(createManualMariaDBContainer());
      case "camundaWithMySQL" ->
          createCamundaRdbmsTestApplication().withDatabaseContainer(createDefaultMySQLContainer());
      case "camundaWithManualMySQL" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
              .withDatabaseContainer(createManualMySQLContainer());
      case "camundaWithOracleDB" ->
          createCamundaRdbmsTestApplication().withDatabaseContainer(createDefaultOracleContainer());
      case "camundaWithManualOracleDB" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
              .withDatabaseContainer(createManualOracleContainer());
      case "camundaWithMssqlDB" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> c.getData().getSecondaryStorage().getRdbms().setUsername("sa"))
              .withDatabaseContainer(createDefaultMSSQLServerContainer());
      case "camundaWithMssqlReplicationCluster" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> {
                    final var rdbms = c.getData().getSecondaryStorage().getRdbms();
                    rdbms.setUsername("sa");
                    rdbms.getAsyncReplication().setEnabled(true);
                    rdbms.getAsyncReplication().setMinSyncReplicas(1);
                  })
              .withDatabaseContainer(new MSSQLReplicationClusterContainer());
      case "camundaWithManualMssqlDB" ->
          createCamundaRdbmsTestApplication()
              .withUnifiedConfig(
                  c -> c.getData().getSecondaryStorage().getRdbms().setAutoDdl(false))
              .withDatabaseContainer(createManualMSSQLServerContainer());
      default -> throw new IllegalArgumentException("Unknown RDBMS test application: " + key);
    };
  }

  private static CamundaRdbmsTestApplication createCamundaRdbmsTestApplication() {
    return new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class).withRdbms();
  }
}
