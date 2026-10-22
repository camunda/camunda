/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class MaintenanceConfigTest {

  @Test
  void shouldRollUpAfterADayIntoRollupsOf256MiBAndKeepEverythingByDefault() {
    // when
    final var config = MaintenanceConfig.defaults();

    // then
    assertThat(config.rollupAge()).isEqualTo(Duration.ofDays(1));
    assertThat(config.rollupTargetBytes()).isEqualTo(256L * 1024 * 1024);
    assertThat(config.retention()).isNull();
  }

  @Test
  void shouldAllowARollupAgeOfZero() {
    assertThat(new MaintenanceConfig(Duration.ZERO, 1, null).rollupAge()).isZero();
  }

  @Test
  void shouldRejectANegativeRollupAge() {
    assertThatThrownBy(() -> new MaintenanceConfig(Duration.ofSeconds(-1), 1, null))
        .isInstanceOfSatisfying(
            InvalidMaintenanceConfigException.class,
            e -> assertThat(e.setting()).isEqualTo("rollupAge"))
        .hasMessage("Expected rollupAge to be 0 or more, but got PT-1S");
  }

  @Test
  void shouldRejectATargetSizeBelowOne() {
    assertThatThrownBy(() -> new MaintenanceConfig(Duration.ZERO, 0, null))
        .isInstanceOfSatisfying(
            InvalidMaintenanceConfigException.class,
            e -> assertThat(e.setting()).isEqualTo("rollupTargetBytes"))
        .hasMessage("Expected rollupTargetBytes to be at least 1, but got 0");
  }

  @Test
  void shouldRejectARetentionThatIsNotLongerThanZero() {
    assertThatThrownBy(() -> new MaintenanceConfig(Duration.ZERO, 1, Duration.ZERO))
        .isInstanceOfSatisfying(
            InvalidMaintenanceConfigException.class,
            e -> assertThat(e.setting()).isEqualTo("retention"))
        .hasMessage("Expected retention to be longer than 0, or unset, but got PT0S");
  }
}
