/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class AssignMappingRuleToTenantTest {

  private static final String TENANT_ID = "tenantId";
  private static final String CLAIM_NAME = "claimName";
  private static final String CLAIM_VALUE = "claimValue";
  private static final String NAME = "name";
  private static final String ID = "mapping-id";

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private String mappingId;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();

    // Create Tenant
    client.newCreateTenantCommand().tenantId(TENANT_ID).name("Initial Tenant Name").send().join();

    // Create Mapping

    client
        .newCreateMappingRuleCommand()
        .claimName(CLAIM_NAME)
        .claimValue(CLAIM_VALUE)
        .name(NAME)
        .mappingRuleId(ID)
        .send()
        .join();
    mappingId = ID;
  }

  @Test
  void shouldAssignMappingToTenant() {
    // When
    client
        .newAssignMappingRuleToTenantCommand()
        .mappingRuleId(ID)
        .tenantId(TENANT_ID)
        .send()
        .join();

    // Then
    ZeebeAssertHelper.assertEntityAssignedToTenant(
        TENANT_ID,
        ID,
        tenant -> {
          assertThat(tenant.getTenantId()).isEqualTo(TENANT_ID);
          assertThat(tenant.getEntityType()).isEqualTo(EntityType.MAPPING_RULE);
        });
  }

  @Test
  void shouldRejectAssignIfTenantDoesNotExist() {
    // Given
    final var invalidTenantId = "invalidTenantId";

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingRuleToTenantCommand()
                    .mappingRuleId(mappingId)
                    .tenantId(invalidTenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'ADD_ENTITY' rejected with code 'NOT_FOUND': Expected to add entity to tenant with ID '%s', but no tenant with this ID exists."
                .formatted(invalidTenantId));
  }

  @Test
  void shouldRejectAssignIfMappingDoesNotExist() {
    // Given
    final String invalidMappingId = "invalid-id";

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingRuleToTenantCommand()
                    .mappingRuleId(invalidMappingId)
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to add mapping_rule with ID '%s' to tenant with ID '%s', but the mapping_rule doesn't exist."
                .formatted(invalidMappingId, TENANT_ID));
  }

  @Test
  void shouldRejectAssignIfTenantAlreadyAssignedToMapping() {
    // given
    client
        .newAssignMappingRuleToTenantCommand()
        .mappingRuleId(ID)
        .tenantId(TENANT_ID)
        .send()
        .join();

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingRuleToTenantCommand()
                    .mappingRuleId(ID)
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to add mapping_rule with ID '%s' to tenant with ID '%s', but the mapping_rule is already assigned to the tenant."
                .formatted(ID, TENANT_ID));
  }
}
