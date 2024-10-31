/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantDeleteProcessorTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDeleteTenant() {
    // Given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        engine.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    final TenantState tenantState = engine.getProcessingState().getTenantState();
    assertThat(tenantState.getTenantByKey(tenantKey).get()).isNotNull();
    // When
    engine.tenant().deleteTenant(tenantKey).delete();
    // Then
    engine.getProcessingState().getTenantState().getTenantByKey(tenantKey);
    assertTrue(engine.getProcessingState().getTenantState().getTenantByKey(tenantKey).isEmpty());
  }

  @Test
  public void shouldRejectIfTenantDoesNotExist() {
    // When
    final var notPresentTenantKey = 1L;
    final var rejectedDeleteRecord =
        engine.tenant().deleteTenant(notPresentTenantKey).expectRejection().delete();
    // Then
    assertThat(rejectedDeleteRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete tenant with key '%s', but no tenant with this key exists."
                .formatted(notPresentTenantKey));
  }
}
