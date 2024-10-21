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
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AddEntityTenantTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldAddEntityToTenant() {
    // given
    final var userKey =
        ENGINE
            .user()
            .newUser("username")
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create()
            .getValue()
            .getUserKey();

    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        ENGINE
            .tenant()
            .newTenant()
            .withTenantId(tenantId)
            .withName("Tenant 1")
            .create()
            .getValue()
            .getTenantKey();

    // when add user entity to tenant
    final var updatedTenant =
        ENGINE
            .tenant()
            .addEntity(tenantKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    // then assert that the entity was added correctly
    Assertions.assertThat(updatedTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("entityKey", userKey);
  }

  @Test
  public void shouldRejectIfTenantIsNotPresentWhileAddingEntity() {
    // when create a tenant
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord =
        ENGINE.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();

    // when try adding entity to a non-existent tenant
    final var notPresentTenantKey = 1L;
    final var notPresentUpdateRecord =
        ENGINE.tenant().addEntity(notPresentTenantKey).expectRejection().add();

    // then
    final var createdTenant = tenantRecord.getValue();
    Assertions.assertThat(createdTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("name", "Tenant 1");

    // assert that the rejection is for tenant not found
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add entity to tenant with key '"
                + notPresentTenantKey
                + "', but no tenant with this key exists.");
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentWhileAddingToTenant() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord =
        ENGINE.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();

    // when try adding a non-existent entity to the tenant
    final var tenantKey = tenantRecord.getValue().getTenantKey();
    final var notPresentUpdateRecord =
        ENGINE
            .tenant()
            .addEntity(tenantKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then assert that the rejection is for entity not found
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add entity with key '1' and type 'USER' to tenant with key '"
                + tenantKey
                + "', but the entity doesn't exist.");
  }
}
