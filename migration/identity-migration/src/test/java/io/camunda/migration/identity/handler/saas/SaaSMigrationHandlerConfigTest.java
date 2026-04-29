/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.config.saas.SaaSMigrationHandlerConfig;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.migration.identity.handler.sm.MigrationHandlerTestDependenciesConfig;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

public class SaaSMigrationHandlerConfigTest {

  @Nested
  @SpringJUnitConfig(
      classes = {
        SaaSMigrationHandlerConfig.class,
        MigrationHandlerTestDependenciesConfig.class,
        SaaSDependencies.class
      })
  @TestPropertySource(properties = {"camunda.migration.identity.mode=CLOUD"})
  class WhenAllHandlersEnabled {

    @Autowired List<MigrationHandler> migrationHandlers;

    @Test
    public void shouldContainAllHandlers() {
      assertThat(migrationHandlers)
          .extracting(handler -> handler.getClass().getSimpleName())
          .containsExactlyInAnyOrder(
              "GroupMigrationHandler",
              "StaticConsoleRoleMigrationHandler",
              "StaticConsoleRoleAuthorizationMigrationHandler",
              "AuthorizationMigrationHandler",
              "ClientMigrationHandler");
    }
  }

  @Nested
  @SpringJUnitConfig(
      classes = {
        SaaSMigrationHandlerConfig.class,
        MigrationHandlerTestDependenciesConfig.class,
        SaaSDependencies.class
      })
  @TestPropertySource(
      properties = {
        "camunda.migration.identity.mode=CLOUD",
        "camunda.migration.identity.handler.cloud.console-role.enabled=false"
      })
  class WhenConsoleRoleHandlerDisabled {

    @Autowired List<MigrationHandler> migrationHandlers;

    @Test
    public void shouldNotContainStaticConsoleRoleMigrationHandler() {
      assertThat(migrationHandlers)
          .extracting(handler -> handler.getClass().getSimpleName())
          .doesNotContain("StaticConsoleRoleMigrationHandler");
    }
  }

  @TestConfiguration
  static class SaaSDependencies {
    @Bean
    public ConsoleClient consoleClient() {
      return mock(ConsoleClient.class);
    }
  }
}
