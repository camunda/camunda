/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class MappingRuleTenancyIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthenticatedAccess();

  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String TENANT_A = "tenantA";
  private static final String TENANT_B = "tenantB";
  private static final String MAPPING_RULE_A = "mappingRuleA";
  private static final String MAPPING_RULE_B = "mappingRuleB";

  @UserDefinition
  private static final TestUser ADMIN_USER = new TestUser(ADMIN, "password", List.of());

  @UserDefinition
  private static final TestUser USER1_USER = new TestUser(USER1, "password", List.of());

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    createTenant(adminClient, TENANT_A);
    createTenant(adminClient, TENANT_B);
    assignUserToTenant(adminClient, ADMIN, TENANT_A);
    assignUserToTenant(adminClient, ADMIN, TENANT_B);
    createMappingRule(adminClient, MAPPING_RULE_A);
    createMappingRule(adminClient, MAPPING_RULE_B);
    waitForMappingRulesBeingExported(adminClient);
  }

  @Test
  public void shouldReturnAllMappingRulesWithTenantAccess(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newMappingRulesSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items().stream().map(MappingRule::getMappingRuleId).toList())
        .containsExactlyInAnyOrder("default", MAPPING_RULE_A, MAPPING_RULE_B);
  }

  @Test
  public void shouldReturnAllMappingRulesWithNoTenantAccess(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newMappingRulesSearchRequest().send().join();
    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items().stream().map(MappingRule::getMappingRuleId).toList())
        .containsExactlyInAnyOrder("default", MAPPING_RULE_A, MAPPING_RULE_B);
  }

  private static void createMappingRule(
      final CamundaClient camundaClient, final String mappingRule) {
    camundaClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRule)
        .name(mappingRule)
        .claimName(mappingRule)
        .claimValue(mappingRule)
        .send()
        .join();
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static void waitForMappingRulesBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(camundaClient.newMappingRulesSearchRequest().send().join().items())
                  .hasSize(3);
            });
  }
}
