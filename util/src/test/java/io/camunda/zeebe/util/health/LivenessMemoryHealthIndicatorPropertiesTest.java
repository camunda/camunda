/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.health;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;

public class LivenessMemoryHealthIndicatorPropertiesTest {

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
    Assertions.assertThat(actual).isEqualTo(0.1, Offset.offset(0.0001));
  }

  @Test
  public void shouldRejectNegativeThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> sutProperties.setThreshold(-0.5))
        .isInstanceOf(IllegalArgumentException.class);
    Assertions.assertThat(sutProperties.getThreshold()).isEqualTo(0.1, Offset.offset(0.0001));
  }

  @Test
  public void shouldRejectZeroThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> sutProperties.setThreshold(-0))
        .isInstanceOf(IllegalArgumentException.class);

    Assertions.assertThat(sutProperties.getThreshold()).isEqualTo(0.1, Offset.offset(0.0001));
  }

  @Test
  public void shouldRejectOneThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> sutProperties.setThreshold(1))
        .isInstanceOf(IllegalArgumentException.class);

    Assertions.assertThat(sutProperties.getThreshold()).isEqualTo(0.1, Offset.offset(0.0001));
  }

  @Test
  public void shouldRejectGreaterThanOneThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> sutProperties.setThreshold(1.5))
        .isInstanceOf(IllegalArgumentException.class);

    Assertions.assertThat(sutProperties.getThreshold()).isEqualTo(0.1, Offset.offset(0.0001));
  }

  @Test
  public void shouldApplyNewThreshold() {
    // when
    sutProperties.setThreshold(0.5);
    final double actual = sutProperties.getThreshold();

    // then
    Assertions.assertThat(actual).isEqualTo(0.5, Offset.offset(0.0001));
  }
}
