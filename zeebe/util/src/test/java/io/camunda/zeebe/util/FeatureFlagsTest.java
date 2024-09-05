/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeatureFlagsTest {

  @Test
  void testDefaultValues() {
    // given
    final var sut = FeatureFlags.createDefault();

    // then
    assertThat(sut.yieldingDueDateChecker()).isTrue();
    assertThat(sut.enableActorMetrics()).isFalse();
    assertThat(sut.enableMessageTTLCheckerAsync()).isFalse();
    assertThat(sut.enablePartitionScaling()).isFalse();
  }

  @Test
  void testDefaultValuesForTests() {
    // given
    final var sut = FeatureFlags.createDefaultForTests();

    // then
    assertThat(sut.yieldingDueDateChecker()).isTrue();
    assertThat(sut.enableMessageTTLCheckerAsync()).isTrue();
    assertThat(sut.enablePartitionScaling()).isTrue();
  }
}
