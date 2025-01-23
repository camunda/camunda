/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.oracle.OracleContainer;

public class CamundaRdbmsInvocationContextProviderExtension
    implements TestTemplateInvocationContextProvider,
        BeforeAllCallback,
        ExtensionContext.Store.CloseableResource {

  private static boolean started = false;

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaRdbmsInvocationContextProviderExtension.class);

  private static final Map<String, CamundaRdbmsTestApplication> SUPPORTED_TEST_APPLICATIONS =
      Map.of(
          "camundaWithH2",
          new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class).withRdbms().withH2(),
          "camundaWithPostgresSQL",
          new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
              .withRdbms()
              .withDatabaseContainer(
                  new PostgreSQLContainer<>("postgres:16-alpine")
                      .withUsername("camunda")
                      .withPassword("camunda")),
          "camundaWithMariaDB",
          new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
              .withRdbms()
              .withDatabaseContainer(
                  new MariaDBContainer<>("mariadb:11.4")
                      .withUsername("camunda")
                      .withPassword("camunda")),
          "camundaWithOracleDB",
          new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
              .withRdbms()
              .withDatabaseContainer(
                  new OracleContainer("gvenzl/oracle-free:latest")
                      .withUsername("camunda")
                      .withPassword("camunda")));

  private final Set<String> useTestApplications;

  public CamundaRdbmsInvocationContextProviderExtension() {
    useTestApplications = SUPPORTED_TEST_APPLICATIONS.keySet();
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
    if (!started) {
      useTestApplications.forEach(
          key -> {
            final CamundaRdbmsTestApplication testApplication =
                SUPPORTED_TEST_APPLICATIONS.get(key);
            LOGGER.info("Start up CamundaDatabaseTestApplication '{}'...", key);
            testApplication.start();
            LOGGER.info("Start up of CamundaDatabaseTestApplication '{}' finished.", key);
          });

      started = true;
      // Your "before all tests" startup logic goes here
      // The following line registers a callback hook when the root test context is shut down
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
            new CamundaDatabaseTestApplicationParameterResolver(
                standaloneCamundaKey, SUPPORTED_TEST_APPLICATIONS.get(standaloneCamundaKey)));
      }
    };
  }

  /**
   * We need to set started to false for the case that the tests should be rerun because of
   * surefire.rerunFailingTestsCount
   */
  @Override
  public void close() throws Throwable {
    LOGGER.info("Resource closed - Close CamundaRdbmsInvocationContextProviderExtension");
    started = false;
  }
}
