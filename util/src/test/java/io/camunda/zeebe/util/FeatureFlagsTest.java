/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
    assertThat(sut.yieldingDueDateChecker()).isFalse();
    assertThat(sut.enableActorMetrics()).isFalse();
    assertThat(sut.enableAsyncScheduledTasks()).isFalse();
  }

  @Test
  void testDefaultValuesForTests() {
    // given
    final var sut = FeatureFlags.createDefaultForTests();

    // then
    assertThat(sut.yieldingDueDateChecker()).isTrue();
    assertThat(sut.enableAsyncScheduledTasks()).isFalse();
  }
}
