/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

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
class AssignMappingToTenantTest {

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
        .newCreateMappingCommand()
        .claimName(CLAIM_NAME)
        .claimValue(CLAIM_VALUE)
        .name(NAME)
        .mappingId(ID)
        .send()
        .join();
    mappingId = ID;
  }

  @Test
  void shouldAssignMappingToTenant() {
    // When
    client.newAssignMappingToTenantCommand(TENANT_ID).mappingId(ID).send().join();

    // Then
    ZeebeAssertHelper.assertEntityAssignedToTenant(
        TENANT_ID,
        ID,
        tenant -> {
          assertThat(tenant.getTenantId()).isEqualTo(TENANT_ID);
          assertThat(tenant.getEntityType()).isEqualTo(EntityType.MAPPING);
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
                    .newAssignMappingToTenantCommand(invalidTenantId)
                    .mappingId(mappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'ADD_ENTITY' rejected with code 'NOT_FOUND': Expected to add entity to tenant with id '%s', but no tenant with this id exists."
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
                    .newAssignMappingToTenantCommand(TENANT_ID)
                    .mappingId(invalidMappingId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to add mapping with id '%s' to tenant with id '%s', but the mapping doesn't exist."
                .formatted(invalidMappingId, TENANT_ID));
  }

  @Test
  void shouldRejectAssignIfTenantAlreadyAssignedToMapping() {
    // given
    client.newAssignMappingToTenantCommand(TENANT_ID).mappingId(ID).send().join();

    // When / Then
    assertThatThrownBy(
            () -> client.newAssignMappingToTenantCommand(TENANT_ID).mappingId(ID).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to add mapping with id '%s' to tenant with id '%s', but the mapping is already assigned to the tenant."
                .formatted(ID, TENANT_ID));
  }
}
