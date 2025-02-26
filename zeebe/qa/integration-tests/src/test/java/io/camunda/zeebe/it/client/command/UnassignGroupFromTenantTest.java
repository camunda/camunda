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
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled(
    "Disabled while groups are not fully supported yet: https://github.com/camunda/camunda/issues/26961 ")
@ZeebeIntegration
class UnassignGroupFromTenantTest {

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
            .tenantId("tenantId")
            .name("Tenant Name")
            .send()
            .join()
            .getTenantKey();

    groupKey = client.newCreateGroupCommand().name("group").send().join().getGroupKey();

    // Assign group to tenant to set up test scenario
    client.newAssignGroupToTenantCommand(tenantKey).groupKey(groupKey).send().join();
  }

  @Test
  void shouldUnassignGroupFromTenant() {
    // when
    client.newUnassignGroupFromTenantCommand(tenantKey).groupKey(groupKey).send().join();

    // then
    ZeebeAssertHelper.assertGroupUnassignedFromTenant(
        tenantKey,
        (tenant) -> {
          assertThat(tenant.getTenantKey()).isEqualTo(tenantKey);
          assertThat(tenant.getEntityKey()).isEqualTo(groupKey);
        });
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    final long nonExistentTenantKey = 999999L;

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignGroupFromTenantCommand(nonExistentTenantKey)
                    .groupKey(groupKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to remove entity from tenant with key '%d', but no tenant with this key exists."
                .formatted(nonExistentTenantKey));
  }

  @Test
  void shouldRejectIfGroupDoesNotExist() {
    // given
    final long nonExistentGroupKey = 888888L;

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignGroupFromTenantCommand(tenantKey)
                    .groupKey(nonExistentGroupKey)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            " Expected to remove entity with key '%d' from tenant with key '%d', but the entity does not exist."
                .formatted(nonExistentGroupKey, tenantKey));
  }

  @Test
  void shouldRejectIfGroupNotAssignedToTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    // Group key is not assigned
                    .newUnassignGroupFromTenantCommand(tenantKey)
                    .groupKey(groupKey + 1)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to remove entity with key '%d' from tenant with key '%d', but the entity does not exist."
                .formatted(groupKey + 1, tenantKey));
  }
}
