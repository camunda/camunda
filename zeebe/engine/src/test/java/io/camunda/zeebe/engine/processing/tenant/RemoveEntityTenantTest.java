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
  public void shouldRemoveEntityToTenant() {
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        engine.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
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
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();

    // when
    final var notPresenttenantKey = 1L;
    final var notPresentUpdateRecord =
        engine.role().addEntity(notPresenttenantKey).expectRejection().add();

    final var createdTenant = tenantRecord.getValue();
    Assertions.assertThat(createdTenant)
        .isNotNull()
        .hasFieldOrPropertyWithValue("tenantId", tenantId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update role with key '"
                + notPresenttenantKey
                + "', but a role with this key does not exist.");
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
            "Expected to remove entity with key '%s' from tenant with key '%s', but the entity doesn't exist."
                .formatted(1L, tenantKey));
  }
}
