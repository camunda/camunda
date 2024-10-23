/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class ClockProcessors {
  private ClockProcessors() {}

  public static void addClockProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ControllableStreamClock clock,
      final CommandDistributionBehavior commandDistributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final var clockProcessor =
        new ClockProcessor(
            writers, keyGenerator, clock, commandDistributionBehavior, authCheckBehavior);
    typedRecordProcessors.onCommand(ValueType.CLOCK, ClockIntent.PIN, clockProcessor);
    typedRecordProcessors.onCommand(ValueType.CLOCK, ClockIntent.RESET, clockProcessor);
  }
}
