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
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class AssignGroupToTenantTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private String tenantId;
  private String groupId;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    tenantId =
        client
            .newCreateTenantCommand()
            .tenantId(Strings.newRandomValidIdentityId())
            .name("Tenant Name")
            .send()
            .join()
            .getTenantId();

    groupId =
        client
            .newCreateGroupCommand()
            .groupId(Strings.newRandomValidIdentityId())
            .name("group")
            .send()
            .join()
            .getGroupId();
  }

  @Test
  void shouldAssignGroupToTenant() {
    // when
    client.newAssignGroupToTenantCommand().groupId(groupId).tenantId(tenantId).send().join();

    // then
    ZeebeAssertHelper.assertEntityAssignedToTenant(
        tenantId,
        groupId,
        tenant -> assertThat(tenant.getEntityType()).isEqualTo(EntityType.GROUP));
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    final var nonExistentTenantId = UUID.randomUUID().toString();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignGroupToTenantCommand()
                    .groupId(groupId)
                    .tenantId(nonExistentTenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to add entity to tenant with ID '%s', but no tenant with this ID exists."
                .formatted(nonExistentTenantId));
  }

  @Test
  void shouldRejectIfGroupDoesNotExist() {
    // given
    final String nonExistentGroupId = Strings.newRandomValidIdentityId();

    // when
    client
        .newAssignGroupToTenantCommand()
        .groupId(nonExistentGroupId)
        .tenantId(tenantId)
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertEntityAssignedToTenant(
        tenantId,
        nonExistentGroupId,
        tenant -> assertThat(tenant.getEntityType()).isEqualTo(EntityType.GROUP));
  }

  @Test
  void shouldRejectIfAlreadyAssigned() {
    // given
    client.newAssignGroupToTenantCommand().groupId(groupId).tenantId(tenantId).send().join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignGroupToTenantCommand()
                    .groupId(groupId)
                    .tenantId(tenantId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to add group with ID '%s' to tenant with ID '%s', but the group is already assigned to the tenant."
                .formatted(groupId, tenantId));
  }

  @Test
  void shouldRejectIfMissingTenantId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignGroupToTenantCommand()
                    .groupId(groupId)
                    .tenantId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectIfEmptyTenantId() {
    // when / then
    assertThatThrownBy(
            () ->
                client.newAssignGroupToTenantCommand().groupId(groupId).tenantId("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignGroupToTenantCommand()
                    .groupId(null)
                    .tenantId(tenantId)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(
            () ->
                client.newAssignGroupToTenantCommand().groupId("").tenantId(tenantId).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }
}
