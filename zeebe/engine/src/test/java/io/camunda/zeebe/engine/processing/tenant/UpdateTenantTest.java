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
        ENGINE.tenant().updateTenant(tenantId).withName(newName).update();

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
    final var nonExistentTenantId = UUID.randomUUID().toString();
    final var notPresentUpdateRecord =
        ENGINE
            .tenant()
            .updateTenant(nonExistentTenantId)
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
            "Expected to update tenant with id '"
                + nonExistentTenantId
                + "', but no tenant with this id exists.");
  }
}
