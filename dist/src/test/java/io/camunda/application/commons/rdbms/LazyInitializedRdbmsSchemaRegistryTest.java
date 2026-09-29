/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.pt.PerTenantSchemaInitialization.Deferral;
import io.camunda.application.commons.pt.PerTenantSchemaInitialization.DeferralCheck;
import io.camunda.db.rdbms.NoopSchemaManager;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LazyInitializedRdbmsSchemaRegistryTest {

  private static final String TENANT = "tenant-a";

  @Test
  void shouldReportNoTenantAsInitializedBeforeTheInitializerIsBound() {
    // given - the broker, and with it the exporter, is created before the initializer
    final var registry = new LazyInitializedRdbmsSchemaRegistry();

    // when / then
    assertThat(registry.isInitialized(TENANT)).isFalse();
  }

  @Test
  void shouldReportTheBoundInitializersReadiness() throws Exception {
    // given
    final var registry = new LazyInitializedRdbmsSchemaRegistry();
    final var initializer =
        new RdbmsSchemaInitializer(
            Map.of(TENANT, new NoopSchemaManager()),
            ignored -> new RetryConfiguration(),
            DeferralCheck.of(ignored -> Deferral.NONE));
    registry.bind(initializer);

    try {
      // when
      initializer.afterPropertiesSet();

      // then
      assertThat(registry.isInitialized(TENANT)).isTrue();
      assertThat(registry.isInitialized("no-such-tenant")).isFalse();
    } finally {
      initializer.destroy();
    }
  }
}
