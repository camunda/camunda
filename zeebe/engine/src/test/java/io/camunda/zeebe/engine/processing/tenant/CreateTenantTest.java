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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class CreateTenantTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateTenant() {
    // when
    final var createdTenantRecord =
        ENGINE.tenant().newTenant().withTenantId("tenant-123").withName("New Tenant").create();

    // then
    final var createdTenant = createdTenantRecord.getValue();
    assertThat(createdTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("tenantId", "tenant-123")
        .hasFieldOrPropertyWithValue("name", "New Tenant")
        .hasFieldOrProperty("tenantKey")
        .extracting("tenantKey", InstanceOfAssertFactories.LONG)
        .isGreaterThan(0L);
  }

  @DisplayName("should reject tenant create command when tenant key already exists")
  @Test
  public void shouldNotDuplicateTenant() {
    final var tenantId = UUID.randomUUID().toString();
    // given
    ENGINE.tenant().newTenant().withTenantId(tenantId).withName("Existing Tenant").create();

    // when
    final var duplicateTenantRecord =
        ENGINE
            .tenant()
            .newTenant()
            .withTenantId(tenantId)
            .withName("Duplicate Tenant")
            .expectRejection()
            .create();

    // then
    assertThat(duplicateTenantRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create tenant with ID '"
                + tenantId
                + "', but a tenant with this ID already exists");
  }
}
