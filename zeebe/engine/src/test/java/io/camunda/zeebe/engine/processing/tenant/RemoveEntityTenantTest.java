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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RemoveEntityTenantTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRemoveEntityFromTenant() {
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        engine
            .tenant()
            .newTenant()
            .withTenantId(tenantId)
            .withName("name")
            .create()
            .getValue()
            .getTenantKey();
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    engine
        .tenant()
        .addEntity(tenantKey)
        .withEntityKey(userKey)
        .withEntityType(EntityType.USER)
        .add();
    final var removedEntity =
        engine
            .tenant()
            .removeEntity(tenantKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    Assertions.assertThat(removedEntity)
        .isNotNull()
        .hasFieldOrPropertyWithValue("tenantKey", tenantKey)
        .hasFieldOrPropertyWithValue("entityKey", userKey)
        .hasFieldOrPropertyWithValue("entityType", EntityType.USER);
  }

  @Test
  public void shouldRejectIfTenantIsNotPresentEntityRemoval() {
    final var notPresentTenantKey = 1L;
    final var notPresentUpdateRecord =
        engine.tenant().removeEntity(notPresentTenantKey).expectRejection().remove();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity from tenant with key '"
                + notPresentTenantKey
                + "', but no tenant with this key exists.");
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentEntityRemoval() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();

    // when
    final var createdTenant = tenantRecord.getValue();
    final var tenantKey = createdTenant.getTenantKey();
    final var notPresentUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    Assertions.assertThat(createdTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("tenantId", tenantId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity with key '%s' from tenant with key '%s', but the entity does not exist."
                .formatted(1L, tenantKey));
  }

  @Test
  public void shouldRejectIfEntityIsNotAssigned() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();

    // when
    final var createdTenant = tenantRecord.getValue();
    final var tenantKey = createdTenant.getTenantKey();
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var notAssignedUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    assertThat(notAssignedUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity with key '%s' from tenant with key '%s', but the entity is not assigned to this tenant."
                .formatted(userKey, tenantKey));
  }
}
