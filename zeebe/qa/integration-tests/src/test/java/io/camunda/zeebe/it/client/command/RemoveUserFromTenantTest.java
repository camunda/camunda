/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.protocol.record.value.EntityType.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class RemoveUserFromTenantTest {

  private static final String TENANT_ID = "tenantId";
  private static final String USERNAME = "username";

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();

    // Create Tenant
    client.newCreateTenantCommand().tenantId(TENANT_ID).name("Tenant Name").send().join();

    // Create User
    client
        .newUserCreateCommand()
        .username(USERNAME)
        .name("name")
        .email("email@example.com")
        .password("password")
        .send()
        .join();

    // Assign User to Tenant
    client.newAssignUserToTenantCommand(TENANT_ID).username(USERNAME).send().join();
  }

  @Test
  void shouldRemoveUserFromTenant() {
    // When
    client.newUnassignUserFromTenantCommand(TENANT_ID).username(USERNAME).send().join();

    // Then
    ZeebeAssertHelper.assertEntityRemovedFromTenant(
        TENANT_ID, USERNAME, tenant -> assertThat(tenant.getEntityType()).isEqualTo(USER));
  }

  @Test
  void shouldRejectUnassignIfTenantDoesNotExist() {
    // Given
    final var invalidTenantId = Strings.newRandomValidIdentityId();

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignUserFromTenantCommand(invalidTenantId)
                    .username(USERNAME)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND': Expected to remove entity from tenant '%s', but no tenant with this id exists."
                .formatted(invalidTenantId));
  }

  @Test
  void shouldRejectUnassignIfUserIsNotAssignedToTenant() {
    // Given
    final var unassignedUsername = "username2";
    client
        .newUserCreateCommand()
        .username(unassignedUsername)
        .name("name2")
        .email("email2@example.com")
        .password("password")
        .send()
        .join();

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignUserFromTenantCommand(TENANT_ID)
                    .username(unassignedUsername)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND': Expected to remove user with ID '%s' from tenant with ID '%s', but the user is not assigned to this tenant."
                .formatted(unassignedUsername, TENANT_ID));
  }
}
