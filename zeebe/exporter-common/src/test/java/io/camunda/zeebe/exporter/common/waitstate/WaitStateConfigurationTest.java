/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WaitStateConfigurationTest {

  @Test
  void shouldBeEnabledByDefault() {
    assertThat(new WaitStateConfiguration().isEnabled()).isTrue();
  }

  @Test
  void shouldAllowDisabling() {
    // given / when
    final var config = new WaitStateConfiguration().setEnabled(false);

    // then
    assertThat(config.isEnabled()).isFalse();
  }

  @Test
  void shouldAllowReEnabling() {
    // given
    final var config = new WaitStateConfiguration().setEnabled(false);

    // when
    config.setEnabled(true);

    // then
    assertThat(config.isEnabled()).isTrue();
  }
}
