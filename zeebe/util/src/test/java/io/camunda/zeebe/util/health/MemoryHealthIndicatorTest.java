/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

public class MemoryHealthIndicatorTest {

  @Test
  public void shouldRejectNegativeThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> new MemoryHealthIndicator(-0.5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectZeroThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> new MemoryHealthIndicator(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectOneThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> new MemoryHealthIndicator(1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectGreaterThanOneThreshold() {
    // when + then
    Assertions.assertThatThrownBy(() -> new MemoryHealthIndicator(1.5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldUseThresholdPassedInThroughConstructor() {
    // when
    final MemoryHealthIndicator sutHealthIndicaor = new MemoryHealthIndicator(0.5);

    final double actual = sutHealthIndicaor.getThreshold();

    // then
    Assertions.assertThat(actual).isEqualTo(0.5, Offset.offset(0.001));
  }

  @Test
  public void shouldReturnUpWhenEnoughAvailableMemory() {
    // given
    final double availablePercentageCurrently = getAvailablePercentageCurrently();
    final double thresholdBelowCurrentLevel = availablePercentageCurrently * 0.9;
    final MemoryHealthIndicator sutHealthIndicator =
        new MemoryHealthIndicator(thresholdBelowCurrentLevel);

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    Assertions.assertThat(actual.getStatus()).isSameAs(Status.UP);
  }

  @Test
  public void shouldReturnDownWhenNotEnoughAvailableMemory() {
    // given
    final double availablePercentageCurrently = getAvailablePercentageCurrently();
    final double thresholdAboveCurrentLevel =
        ((1 - availablePercentageCurrently) * 0.1) + availablePercentageCurrently;

    final MemoryHealthIndicator sutHealthIndicator =
        new MemoryHealthIndicator(thresholdAboveCurrentLevel);

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    Assertions.assertThat(actual.getStatus()).isSameAs(Status.DOWN);
  }

  private double getAvailablePercentageCurrently() {
    final Runtime runtime = Runtime.getRuntime();
    final long freeMemory = runtime.freeMemory();
    final long totalMemory = runtime.totalMemory();
    final long maxMemory = runtime.maxMemory();

    final long availableMemory = freeMemory + (maxMemory - totalMemory);

    return (double) (availableMemory) / maxMemory;
  }
}
