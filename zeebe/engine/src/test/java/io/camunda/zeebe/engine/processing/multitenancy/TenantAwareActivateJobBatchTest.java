/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareActivateJobBatchTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectActivateJobBatchForUnauthorizedTenant() {
    // given
    final var username = UUID.randomUUID().toString();
    final var tenantId = UUID.randomUUID().toString();
    final var user = ENGINE.user().newUser(username).create().getValue();
    final var tenantKey =
        ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    // when
    final var tenantIds = new java.util.ArrayList<>(List.of("custom-tenant", "another-tenant"));
    final var rejection =
        ENGINE
            .jobs()
            .withTenantIds(tenantIds)
            .withType("test")
            .expectRejection()
            .activate(user.getUsername());

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.UNAUTHORIZED)
        .hasRejectionReason(
            "Expected to activate job batch for tenants '%s', but user is not authorized. Authorized tenants are '%s'"
                .formatted(tenantIds, List.of(tenantId)));
  }

  @Test
  public void shouldRejectActivateJobBatchIfNOTAllTenantAreAuthorized() {
    // given
    final var username = UUID.randomUUID().toString();
    final var tenantId = UUID.randomUUID().toString();
    final var user = ENGINE.user().newUser(username).create().getValue();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    // when
    final var tenantIds =
        new java.util.ArrayList<>(List.of("custom-tenant", "another-tenant", "tenantId"));
    final var rejection =
        ENGINE
            .jobs()
            .withTenantIds(tenantIds)
            .withType("test")
            .expectRejection()
            .activate(user.getUsername());

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.UNAUTHORIZED)
        .hasRejectionReason(
            "Expected to activate job batch for tenants '%s', but user is not authorized. Authorized tenants are '%s'"
                .formatted(tenantIds, List.of(tenantId)));
  }
}
