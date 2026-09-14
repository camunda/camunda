/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.suspender;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SuspensionMeterTest {

  @Test
  void shouldBuildTargetVariablesWithRequestedFanOut() {
    // given / when
    final var variables = SuspensionMeter.buildTargetVariables(500, 500, 200, "i0");

    // then - the multi-instance collections drive the fan-out size
    assertThat(variables).containsOnlyKeys("jobs", "subs", "timers");
    assertThat((List<?>) variables.get("jobs")).hasSize(500);
    assertThat((List<?>) variables.get("subs")).hasSize(500);
    assertThat((List<?>) variables.get("timers")).hasSize(200);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldPrefixSubscriptionKeysSoTheyAreUniqueAndDistinct() {
    // given / when
    final var variables = SuspensionMeter.buildTargetVariables(0, 3, 0, "i7");

    // then - subscription keys are prefixed per instance and distinct within the instance, so
    // subscriptions never collide across target instances
    assertThat((List<String>) variables.get("subs"))
        .containsExactly("i7-0", "i7-1", "i7-2")
        .doesNotHaveDuplicates();
  }
}
