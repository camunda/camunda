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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class RemoveEntityTenantTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRemoveEntityFromTenant() {
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

    ENGINE.tenant().addEntity(tenantKey).withEntityKey(userKey).add();

    // when remove user entity from tenant
    final var updatedTenant =
        ENGINE.tenant().removeEntity(tenantKey).withEntityKey(userKey).remove().getValue();

    // then assert that the entity was removed correctly
    Assertions.assertThat(updatedTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("tenantKey", tenantKey)
        .hasFieldOrPropertyWithValue("entityType", USER)
        .extracting("entityKey")
        .isNull(); // Entity should be removed
  }

  @Test
  public void shouldRejectIfTenantIsNotPresentWhileRemovingEntity() {
    // when try removing entity from a non-existent tenant
    final var entityKey = 1L;
    final var notPresentUpdateRecord =
        ENGINE.tenant().removeEntity(entityKey).expectRejection().remove();
    // then assert that the rejection is for tenant not found
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity from tenant with key '%s', but no tenant with this key exists."
                .formatted(entityKey));
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentWhileRemovingFromTenant() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord =
        ENGINE.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();

    // when try removing a non-existent entity from the tenant
    final var tenantKey = tenantRecord.getValue().getTenantKey();
    final var notPresentUpdateRecord =
        ENGINE
            .tenant()
            .removeEntity(tenantKey)
            .withEntityKey(1L)
            .withEntityType(USER)
            .expectRejection()
            .remove();

    // then assert that the rejection is for entity not found
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity with key '1' from tenant with key '%s', but the entity doesn't exist."
                .formatted(tenantKey));
  }
}
