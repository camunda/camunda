/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migration.identity.config.sm.SMOidcMigrationHandlerConfig;
import io.camunda.migration.identity.handler.MigrationHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {SMOidcMigrationHandlerConfig.class, MigrationHandlerTestDependenciesConfig.class})
@TestPropertySource(properties = {"camunda.migration.identity.mode=OIDC"})
public class SMOidcMigrationHandlerConfigTest {

  @Autowired List<MigrationHandler> migrationHandlers;

  @Test
  public void shouldContainHandlersInCorrectOrder() {
    final List<String> expectedOrder =
        List.of("RoleMigrationHandler", "TenantMigrationHandler", "MappingRuleMigrationHandler");
    assertThat(migrationHandlers)
        .extracting(handler -> handler.getClass().getSimpleName())
        .containsExactlyElementsOf(expectedOrder);
  }
}
