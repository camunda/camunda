/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.sampling;

import static io.camunda.exporter.analytics.sampling.HashSampler.MAX_SAMPLE_RATE;
import static io.camunda.exporter.analytics.sampling.HashSampler.MIN_SAMPLE_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HashSamplerTest {

  @Test
  void shouldSampleAllWhenRateIsMax() {
    for (int i = 0; i < 100; i++) {
      assertThat(HashSampler.shouldSample(i, MAX_SAMPLE_RATE)).isTrue();
    }
  }

  @Test
  void shouldSampleNoneWhenRateIsMin() {
    for (int i = 0; i < 100; i++) {
      assertThat(HashSampler.shouldSample(i, MIN_SAMPLE_RATE)).isFalse();
    }
  }

  @Test
  void shouldClampRateAboveOneToFullSampling() {
    for (int i = 0; i < 100; i++) {
      assertThat(HashSampler.shouldSample(i, 1.5)).isTrue();
    }
  }

  @Test
  void shouldClampRateBelowZeroToNoSampling() {
    for (int i = 0; i < 100; i++) {
      assertThat(HashSampler.shouldSample(i, -0.5)).isFalse();
    }
  }

  @Test
  void shouldDistributeUniformlyAtOnePercent() {
    // given
    final int total = 10_000;

    // when
    int count = 0;
    for (int i = 0; i < total; i++) {
      if (HashSampler.shouldSample(i, 0.01)) {
        count++;
      }
    }

    // then
    assertThat(count).isCloseTo(100, within(20));
  }

  @Test
  void shouldDistributeUniformlyAtFiftyPercent() {
    // given
    final int total = 10_000;

    // when
    int count = 0;
    for (int i = 0; i < total; i++) {
      if (HashSampler.shouldSample(i, 0.5)) {
        count++;
      }
    }

    // then
    assertThat(count).isCloseTo(5000, within(500));
  }

  @Test
  void shouldDistributeUniformlyForSparsePositions() {
    // given — 100 events at every 1000th position
    int count = 0;
    for (int i = 0; i < 100; i++) {
      if (HashSampler.shouldSample(1L + (long) i * 1000, 0.1)) {
        count++;
      }
    }

    // then
    assertThat(count).isCloseTo(10, within(5));
  }

  @Test
  void shouldProduceSameResultsOnReplay() {
    // given
    final int total = 1_000;
    final var firstPass = new ArrayList<Boolean>(total);
    final var secondPass = new ArrayList<Boolean>(total);

    // when
    for (int i = 1; i <= total; i++) {
      firstPass.add(HashSampler.shouldSample(i, 0.1));
    }
    for (int i = 1; i <= total; i++) {
      secondPass.add(HashSampler.shouldSample(i, 0.1));
    }

    // then
    assertThat(secondPass).isEqualTo(firstPass);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, Long.MAX_VALUE, Long.MIN_VALUE})
  void shouldHandleEdgePositions(final long position) {
    assertThatCode(() -> HashSampler.shouldSample(position, 0.5)).doesNotThrowAnyException();
  }
}
