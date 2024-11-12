/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UpdateTenantTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldUpdateTenant() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var createdRecord =
        ENGINE.tenant().newTenant().withTenantId(tenantId).withName("Existing Tenant").create();

    // when
    final var newName = "Updated Tenant Name";
    final var updatedTenantRecord =
        ENGINE
            .tenant()
            .updateTenant(createdRecord.getValue().getTenantKey())
            .withName(newName)
            .update();

    // then
    final var updatedTenant = updatedTenantRecord.getValue();
    Assertions.assertThat(updatedTenant).isNotNull().hasFieldOrPropertyWithValue("name", newName);
  }

  @Test
  public void shouldRejectIfTenantIsNotPresent() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord =
        ENGINE.tenant().newTenant().withTenantId(tenantId).withName("Existing Tenant").create();

    // when
    final var nonExistentTenantKey = 1L;
    final var notPresentUpdateRecord =
        ENGINE
            .tenant()
            .updateTenant(nonExistentTenantKey)
            .withName("New Tenant Name")
            .expectRejection()
            .update();

    // then
    final var createdTenant = tenantRecord.getValue();
    Assertions.assertThat(createdTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "Existing Tenant");

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update tenant with key '"
                + nonExistentTenantKey
                + "', but no tenant with this key exists.");
  }

  @Test
  public void shouldRejectIfTenantWithSameIdExists() {
    // given
    final var tenantId1 = "tenant-123";
    final var tenantId2 = "tenant-456";
    final var tenantKey =
        ENGINE
            .tenant()
            .newTenant()
            .withTenantId(tenantId1)
            .withName("First Tenant")
            .create()
            .getValue()
            .getTenantKey();
    ENGINE.tenant().newTenant().withTenantId(tenantId2).withName("Second Tenant").create();

    // when
    final var updateRecord =
        ENGINE.tenant().updateTenant(tenantKey).withTenantId(tenantId2).expectRejection().update();

    // then
    assertThat(updateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to update tenant with ID '"
                + tenantId2
                + "', but a tenant with this ID already exists.");
  }
}
