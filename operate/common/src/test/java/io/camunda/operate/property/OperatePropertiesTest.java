/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class OperatePropertiesTest {

  @Test
  void shouldEnableNavV2ByDefaultForSelfManaged() {
    // given
    final var properties = new OperateProperties();

    // when
    final boolean result = properties.resolveNavV2Enabled(false);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldDisableNavV2ByDefaultForSaas() {
    // given
    final var properties = new OperateProperties();

    // when
    final boolean result = properties.resolveNavV2Enabled(true);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldEnableNavV2ForSaasWhenExplicitlyConfigured() {
    // given
    final var properties = new OperateProperties();
    properties.setNavV2Enabled(true);

    // when
    final boolean result = properties.resolveNavV2Enabled(true);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldDisableNavV2ForSelfManagedWhenExplicitlyConfigured() {
    // given
    final var properties = new OperateProperties();
    properties.setNavV2Enabled(false);

    // when
    final boolean result = properties.resolveNavV2Enabled(false);

    // then
    assertThat(result).isFalse();
  }
}
