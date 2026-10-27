/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

/**
 * Verifies that {@code SystemContext.validateConfiguration()} checks every physical tenant's own
 * overridable configuration, not just the default/root one: an operator who overrides an
 * overridable property (e.g. {@code data.snapshot-period}) with an invalid value for a non-default
 * physical tenant must have the broker refuse to start, with the offending tenant named in the
 * error — instead of the tenant's invalid configuration going unchecked and the broker booting
 * anyway. A legitimate per-tenant override of the same property must still boot successfully.
 */
@ZeebeIntegration
final class PhysicalTenantSystemContextValidationIT {

  private static final String TENANT_A = "tenanta";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @Test
  void shouldFailToBootWhenPhysicalTenantOverridesInvalidSnapshotPeriod() {
    // given — a non-default physical tenant overriding data.snapshot-period below the one-minute
    // minimum, through the real configuration surface an operator would use
    final var broker = TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());
    broker.withPtConfig(
        TENANT_A, camunda -> camunda.getData().setSnapshotPeriod(Duration.ofSeconds(1)));

    // when/then — startup fails, naming the offending tenant, instead of silently booting with an
    // unvalidated per-tenant configuration
    try {
      assertThatThrownBy(broker::start)
          .isInstanceOf(BeanCreationException.class)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .rootCause()
          .hasMessageContaining("Snapshot period")
          .hasMessageContaining("physical tenant '" + TENANT_A + "'");
    } finally {
      broker.close();
    }
  }

  @Test
  void shouldBootWhenPhysicalTenantOverridesValidSnapshotPeriod() {
    // given — a non-default physical tenant legitimately overriding data.snapshot-period away from
    // the default value
    final var broker = TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());
    broker.withPtConfig(
        TENANT_A, camunda -> camunda.getData().setSnapshotPeriod(Duration.ofMinutes(10)));

    // when/then — a valid per-tenant override does not stop the broker from booting
    try {
      assertThatCode(broker::start).doesNotThrowAnyException();
    } finally {
      broker.close();
    }
  }
}
