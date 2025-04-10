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
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Enable with https://github.com/camunda/camunda/issues/29925")
@ZeebeIntegration
class AssignGroupToTenantTest {

  private static final String TENANT_ID = "tenantId";

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private long tenantKey;
  private long groupKey;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    tenantKey =
        client
            .newCreateTenantCommand()
            .tenantId(TENANT_ID)
            .name("Tenant Name")
            .send()
            .join()
            .getTenantKey();

    groupKey = client.newCreateGroupCommand().name("group").send().join().getGroupKey();
  }

  @Test
  void shouldAssignGroupToTenant() {
    // when
    client.newAssignGroupToTenantCommand(TENANT_ID).groupKey(groupKey).send().join();

    // then
    // TODO remove the String parsing once Groups are migrated to work with ids instead of keys
    ZeebeAssertHelper.assertEntityAssignedToTenant(
        TENANT_ID,
        String.valueOf(groupKey),
        tenant -> {
          assertThat(tenant.getTenantKey()).isEqualTo(tenantKey);
          assertThat(tenant.getEntityType()).isEqualTo(EntityType.GROUP);
        });
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    final var nonExistentTenantId = UUID.randomUUID().toString();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignGroupToTenantCommand(nonExistentTenantId)
                    .groupKey(groupKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to add entity to tenant with id '%s', but no tenant with this id exists."
                .formatted(nonExistentTenantId));
  }

  @Test
  void shouldRejectIfGroupDoesNotExist() {
    // given
    final long nonExistentGroupKey = 888888L;

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignGroupToTenantCommand(TENANT_ID)
                    .groupKey(nonExistentGroupKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to add group with id '%d' to tenant with id '%s', but the group doesn't exist."
                .formatted(nonExistentGroupKey, TENANT_ID));
  }

  @Test
  void shouldRejectIfAlreadyAssigned() {
    // given
    client.newAssignGroupToTenantCommand(TENANT_ID).groupKey(groupKey).send().join();

    // when / then
    assertThatThrownBy(
            () -> client.newAssignGroupToTenantCommand(TENANT_ID).groupKey(groupKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to add group with id '%d' to tenant with id '%s', but the group is already assigned to the tenant."
                .formatted(groupKey, TENANT_ID));
  }
}
