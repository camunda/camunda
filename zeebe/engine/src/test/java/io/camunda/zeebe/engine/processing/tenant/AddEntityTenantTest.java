/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

public class AddEntityTenantTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldAddEntityToTenant() {
    // given
    final var userKey =
        engine
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
        engine
            .tenant()
            .newTenant()
            .withTenantId(tenantId)
            .withName("Tenant 1")
            .create()
            .getValue()
            .getTenantKey();

    // when add user entity to tenant
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantKey)
            .withEntityKey(userKey)
            .withEntityType(USER)
            .add()
            .getValue();

    // then assert that the entity was added correctly
    Assertions.assertThat(updatedTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("entityKey", userKey)
        .hasFieldOrPropertyWithValue("tenantKey", tenantKey)
        .hasFieldOrPropertyWithValue("entityType", USER);
  }

  @Test
  public void shouldRejectIfTenantIsNotPresentWhileAddingEntity() {
    // when try adding entity to a non-existent tenant
    final var entityKey = 1L;
    final var notPresentUpdateRecord = engine.tenant().addEntity(entityKey).expectRejection().add();
    // then assert that the rejection is for tenant not found
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add entity to tenant with key '%s', but no tenant with this key exists."
                .formatted(entityKey));
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentWhileAddingToTenant() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord =
        engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();

    // when try adding a non-existent entity to the tenant
    final var tenantKey = tenantRecord.getValue().getTenantKey();
    final var notPresentUpdateRecord =
        engine
            .tenant()
            .addEntity(tenantKey)
            .withEntityKey(1L)
            .withEntityType(USER)
            .expectRejection()
            .add();

    // then assert that the rejection is for entity not found
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add entity with key '1' to tenant with key '%s', but the entity doesn't exist."
                .formatted(tenantKey));
  }

  @Test
  public void shouldRejectIfEntityIsAlreadyAssignedToTenant() {
    // given
    final var username = UUID.randomUUID().toString();
    final var userKey =
        engine
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create()
            .getValue()
            .getUserKey();
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord =
        engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();
    final var tenantKey = tenantRecord.getValue().getTenantKey();
    engine.tenant().addEntity(tenantKey).withEntityKey(userKey).withEntityType(USER).add();

    // when try adding a non-existent entity to the tenant
    final var alreadyAssignedRecord =
        engine
            .tenant()
            .addEntity(tenantKey)
            .withEntityKey(userKey)
            .withEntityType(USER)
            .expectRejection()
            .add();

    // then assert that the rejection is for entity not found
    assertThat(alreadyAssignedRecord)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to add entity with key '%s' to tenant with key '%s', but the entity is already assigned to the tenant."
                .formatted(userKey, tenantKey));
  }
}
