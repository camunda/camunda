/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import org.junit.Before;
import org.junit.Test;

public class MemoryHealthIndicatorPropertiesTest {

  private MemoryHealthIndicatorProperties sutProperties;

  @Before
  public void setUp() {
    sutProperties = new MemoryHealthIndicatorProperties();
  }

  @Test
  public void shouldHaveDefaultThresholdOfTenPercent() {
    // when
    final double actual = sutProperties.getThreshold();

    // then
    assertThat(actual).isEqualTo(0.1, offset(0.0001));
  }

  @Test
  public void shouldRejectNegativeThreshold() {
    // when + then
    assertThatThrownBy(() -> sutProperties.setThreshold(-0.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(sutProperties.getThreshold()).isEqualTo(0.1, offset(0.0001));
  }

  @Test
  public void shouldRejectZeroThreshold() {
    // when + then
    assertThatThrownBy(() -> sutProperties.setThreshold(-0))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(sutProperties.getThreshold()).isEqualTo(0.1, offset(0.0001));
  }

  @Test
  public void shouldRejectOneThreshold() {
    // when + then
    assertThatThrownBy(() -> sutProperties.setThreshold(1))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(sutProperties.getThreshold()).isEqualTo(0.1, offset(0.0001));
  }

  @Test
  public void shouldRejectGreaterThanOneThreshold() {
    // when + then
    assertThatThrownBy(() -> sutProperties.setThreshold(1.5))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(sutProperties.getThreshold()).isEqualTo(0.1, offset(0.0001));
  }

  @Test
  public void shouldApplyNewThreshold() {
    // when
    sutProperties.setThreshold(0.5);
    final double actual = sutProperties.getThreshold();

    // then
    assertThat(actual).isEqualTo(0.5, offset(0.0001));
  }
}
