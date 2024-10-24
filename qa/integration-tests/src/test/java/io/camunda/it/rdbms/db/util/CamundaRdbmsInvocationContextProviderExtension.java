/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class CamundaRdbmsInvocationContextProviderExtension
    implements TestTemplateInvocationContextProvider, BeforeAllCallback {

  private static boolean started = false;

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaRdbmsInvocationContextProviderExtension.class);

  private final Map<String, CamundaRdbmsTestApplication> testApplications;

  public CamundaRdbmsInvocationContextProviderExtension() {
    testApplications = new HashMap<>();
    testApplications.put(
        "camundaWithPostgresSQL",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withDatabaseContainer(
                new PostgreSQLContainer<>("postgres:16-alpine")
                    .withUsername("user")
                    .withPassword("password")));
    testApplications.put(
        "camundaWithMariaDB",
        new CamundaRdbmsTestApplication(RdbmsTestConfiguration.class)
            .withDatabaseContainer(
                new MariaDBContainer<>("mariadb:11.4")
                    .withUsername("user")
                    .withPassword("password")));
  }

  @Override
  public boolean supportsTestTemplate(final ExtensionContext extensionContext) {
    return true;
  }

  @Override
  public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
      final ExtensionContext extensionContext) {
    return testApplications.keySet().stream().map(this::invocationContext);
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    if (!started) {
      testApplications.entrySet().parallelStream()
          .forEach(
              entry -> {
                LOGGER.info("Start up CamundaDatabaseTestApplication '{}'...", entry.getKey());
                entry.getValue().start();
                LOGGER.info(
                    "Start up of CamundaDatabaseTestApplication '{}' finished.", entry.getKey());
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
                standaloneCamundaKey, testApplications.get(standaloneCamundaKey)));
      }
    };
  }
}
