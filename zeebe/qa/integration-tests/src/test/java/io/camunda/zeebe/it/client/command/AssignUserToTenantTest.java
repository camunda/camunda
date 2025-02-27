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
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class AssignUserToTenantTest {

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
    client
        .newCreateTenantCommand()
        .tenantId(TENANT_ID)
        .name("Tenant Name")
        .send()
        .join()
        .getTenantKey();

    // Create User
    client
        .newUserCreateCommand()
        .username(USERNAME)
        .name("name")
        .email("email@example.com")
        .password("password")
        .send()
        .join()
        .getUserKey();
  }

  @Test
  void shouldAssignUserToTenant() {
    // When
    client.newAssignUserToTenantCommand(TENANT_ID).username(USERNAME).send().join();

    // Then
    assertThat(
            RecordingExporter.tenantRecords()
                .withIntent(TenantIntent.ENTITY_ADDED)
                .withTenantId(TENANT_ID)
                .withEntityType(EntityType.USER)
                .withEntityId(USERNAME)
                .exists())
        .isTrue();
  }

  @Test
  void shouldRejectAssignIfTenantDoesNotExist() {
    // Given
    final var invalidTenantId = Strings.newRandomValidIdentityId();

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToTenantCommand(invalidTenantId)
                    .username(USERNAME)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Command 'ADD_ENTITY' rejected with code 'NOT_FOUND': Expected to add entity to tenant with id '%s', but no tenant with this id exists."
                .formatted(invalidTenantId));
  }

  @Test
  void shouldRejectAssignIfUserDoesNotExist() {
    // Given
    final var invalidUserName = Strings.newRandomValidUsername();

    // When / Then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToTenantCommand(TENANT_ID)
                    .username(invalidUserName)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to add user '%s' to tenant '%s', but the user doesn't exist."
                .formatted(invalidUserName, TENANT_ID));
  }
}
