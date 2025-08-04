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
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class UnassignGroupFromTenantTest {

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

    // Assign group to tenant to set up test scenario
    client.newAssignGroupToTenantCommand().groupId(groupId).tenantId(tenantId).send().join();
  }

  @Test
  void shouldUnassignGroupFromTenant() {
    // when
    client.newUnassignGroupFromTenantCommand(tenantId).groupId(groupId).send().join();

    // then
    ZeebeAssertHelper.assertGroupUnassignedFromTenant(
        tenantId,
        (tenant) -> {
          assertThat(tenant.getTenantId()).isEqualTo(tenantId);
          assertThat(tenant.getEntityId()).isEqualTo(groupId);
        });
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    final var nonExistentTenantId = Strings.newRandomValidIdentityId();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignGroupFromTenantCommand(nonExistentTenantId)
                    .groupId(groupId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to remove entity from tenant '%s', but no tenant with this ID exists."
                .formatted(nonExistentTenantId));
  }

  @Test
  void shouldUnassignIfGroupDoesNotExist() {
    // given
    final String nonExistentGroupId = Strings.newRandomValidIdentityId();
    client
        .newAssignGroupToTenantCommand()
        .groupId(nonExistentGroupId)
        .tenantId(tenantId)
        .send()
        .join();

    // when
    client.newUnassignGroupFromTenantCommand(tenantId).groupId(nonExistentGroupId).send().join();

    // then
    ZeebeAssertHelper.assertGroupUnassignedFromTenant(
        tenantId,
        (tenant) -> {
          assertThat(tenant.getTenantId()).isEqualTo(tenantId);
          assertThat(tenant.getEntityId()).isEqualTo(nonExistentGroupId);
        });
  }

  @Test
  void shouldRejectIfGroupNotAssignedToTenant() {
    // when / then
    final String nonAssignedGroupId = Strings.newRandomValidIdentityId();
    client.newCreateGroupCommand().groupId(nonAssignedGroupId).name("group").send().join();

    assertThatThrownBy(
            () ->
                client
                    // Group key is not assigned
                    .newUnassignGroupFromTenantCommand(tenantId)
                    .groupId(nonAssignedGroupId)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to remove group with ID '%s' from tenant with ID '%s', but the group is not assigned to this tenant."
                .formatted(nonAssignedGroupId, tenantId));
  }

  @Test
  void shouldRejectIfMissingTenantId() {
    // when / then
    assertThatThrownBy(
            () -> client.newUnassignGroupFromTenantCommand(null).groupId(groupId).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectIfEmptyTenantId() {
    // when / then
    assertThatThrownBy(
            () -> client.newUnassignGroupFromTenantCommand("").groupId(groupId).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldRejectIfMissingGroupId() {
    // when / then
    assertThatThrownBy(
            () -> client.newUnassignGroupFromTenantCommand(tenantId).groupId(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRejectIfEmptyGroupId() {
    // when / then
    assertThatThrownBy(
            () -> client.newUnassignGroupFromTenantCommand(tenantId).groupId("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }
}
