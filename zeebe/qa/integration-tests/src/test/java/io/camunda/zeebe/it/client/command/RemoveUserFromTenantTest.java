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
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class RemoveUserFromTenantTest {

  private static final String TENANT_ID = "tenant-id";

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private long tenantKey;
  private long userKey;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();

    // Create Tenant
    tenantKey =
        client
            .newCreateTenantCommand()
            .tenantId(TENANT_ID)
            .name("Tenant Name")
            .send()
            .join()
            .getTenantKey();

    // Create User
    final var username = "username";
    userKey =
        client
            .newUserCreateCommand()
            .username(username)
            .name("name")
            .email("email@example.com")
            .password("password")
            .send()
            .join()
            .getUserKey();

    // Assign User to Tenant
    client.newAssignUserToTenantCommand(TENANT_ID).username(username).send().join();
  }

  @Test
  void shouldRemoveUserFromTenant() {
    // When
    client.newRemoveUserFromTenantCommand(tenantKey).userKey(userKey).send().join();

    // Then
    ZeebeAssertHelper.assertEntityRemovedFromTenant(
        tenantKey, userKey, tenant -> assertThat(tenant.getEntityType()).isEqualTo(USER));
  }

  @Test
  void shouldRejectUnassignIfTenantDoesNotExist() {
    // Given
    final long invalidTenantKey = 99999L;

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newRemoveUserFromTenantCommand(invalidTenantKey)
                    .userKey(userKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND': Expected to remove entity from tenant with key '%d', but no tenant with this key exists."
                .formatted(invalidTenantKey));
  }

  @Test
  void shouldRejectUnassignIfUserDoesNotExist() {
    // Given
    final long invalidUserKey = 99999L;

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newRemoveUserFromTenantCommand(tenantKey)
                    .userKey(invalidUserKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND': Expected to remove entity with key '%d' from tenant with key '%d', but the entity does not exist."
                .formatted(invalidUserKey, tenantKey));
  }

  @Test
  void shouldRejectUnassignIfUserIsNotAssignedToTenant() {
    // Given
    final long unassignedUserKey =
        client
            .newUserCreateCommand()
            .username("username2")
            .name("name2")
            .email("email2@example.com")
            .password("password")
            .send()
            .join()
            .getUserKey();

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newRemoveUserFromTenantCommand(tenantKey)
                    .userKey(unassignedUserKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'REMOVE_ENTITY' rejected with code 'NOT_FOUND': Expected to remove entity with key '%d' from tenant with key '%d', but the entity is not assigned to this tenant."
                .formatted(unassignedUserKey, tenantKey));
  }
}
