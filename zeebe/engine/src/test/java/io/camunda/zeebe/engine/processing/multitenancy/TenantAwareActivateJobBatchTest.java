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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareActivateJobBatchTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getAuthorizations().setEnabled(true));

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectActivateJobBatchForUnauthorizedTenant() {
    // given
    final var userKey = ENGINE.user().newUser("username").create().getValue().getUserKey();
    final var tenantKey =
        ENGINE.tenant().newTenant().withTenantId("tenantId").create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantKey)
        .withEntityType(EntityType.USER)
        .withEntityKey(userKey)
        .add();

    // when
    final var tenantIds = new java.util.ArrayList<>(List.of("custom-tenant", "another-tenant"));
    final var rejection =
        ENGINE.jobs().withTenantIds(tenantIds).withType("test").expectRejection().activate(userKey);

    // then
    tenantIds.addFirst(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(rejection)
        .hasRejectionType(RejectionType.UNAUTHORIZED)
        .hasRejectionReason(
            "User is not authorized to activate jobs for tenants '%s'".formatted(tenantIds));
  }

  @Test
  public void shouldRejectActivateJobBatchIfNOTAllTenantAreAuthorized() {
    // given
    final var userKey = ENGINE.user().newUser("username").create().getValue().getUserKey();
    final var tenantKey =
        ENGINE.tenant().newTenant().withTenantId("tenantId").create().getValue().getTenantKey();
    ENGINE
        .tenant()
        .addEntity(tenantKey)
        .withEntityType(EntityType.USER)
        .withEntityKey(userKey)
        .add();

    // when
    final var tenantIds =
        new java.util.ArrayList<>(List.of("custom-tenant", "another-tenant", "tenantId"));
    final var rejection =
        ENGINE.jobs().withTenantIds(tenantIds).withType("test").expectRejection().activate(userKey);

    // then
    tenantIds.addFirst(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(rejection)
        .hasRejectionType(RejectionType.UNAUTHORIZED)
        .hasRejectionReason(
            "User is not authorized to activate jobs for tenants '%s'".formatted(tenantIds));
  }
}
