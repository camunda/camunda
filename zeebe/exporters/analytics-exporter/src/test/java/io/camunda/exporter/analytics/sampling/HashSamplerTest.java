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

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

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
    long count = 0;
    for (int i = 0; i < total; i++) {
      if (HashSampler.shouldSample(i, 0.01)) {
        count++;
      }
    }

    // then
    assertThat(count).isBetween(50L, 150L);
  }

  @Test
  void shouldDistributeUniformlyAtFiftyPercent() {
    // given
    final int total = 10_000;

    // when
    long count = 0;
    for (int i = 0; i < total; i++) {
      if (HashSampler.shouldSample(i, 0.5)) {
        count++;
      }
    }

    // then
    assertThat(count).isBetween(4500L, 5500L);
  }

  @Test
  void shouldDistributeUniformlyForSparsePositions() {
    // given — 100 events at every 1000th position
    long count = 0;
    for (int i = 0; i < 100; i++) {
      if (HashSampler.shouldSample(1L + (long) i * 1000, 0.1)) {
        count++;
      }
    }

    // then
    assertThat(count).isBetween(5L, 15L);
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

  @Test
  void shouldHandleEdgePositions() {
    HashSampler.shouldSample(0L, 0.5);
    HashSampler.shouldSample(Long.MAX_VALUE, 0.5);
    HashSampler.shouldSample(Long.MIN_VALUE, 0.5);
  }
}
