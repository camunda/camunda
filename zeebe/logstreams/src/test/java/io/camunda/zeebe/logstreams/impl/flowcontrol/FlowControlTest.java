/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import java.time.Duration;
import java.util.LinkedList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class FlowControlTest {

  @Test
  void eventuallyRejects() {
    // given
    final var logStreamMetrics = new LogStreamMetrics(1);
    final var flow = new FlowControl(logStreamMetrics);

    // when - then
    Awaitility.await("Rejects new appends")
        .pollInSameThread()
        .pollInterval(Duration.ZERO)
        .until(() -> flow.tryAcquire().isLeft());
  }

  @Test
  void recoversWhenCompletingAppends() {
    // given
    final var logStreamMetrics = new LogStreamMetrics(1);
    final var flow = new FlowControl(logStreamMetrics);
    // when
    boolean rejecting = false;
    final var inFlight = new LinkedList<InFlightAppend>();
    do {
      final var result = flow.tryAcquire();
      if (result.isLeft()) {
        rejecting = true;
      } else {
        inFlight.push(result.get());
      }
    } while (!rejecting);
    inFlight.forEach(append -> append.onCommit(1));

    // then
    Awaitility.await("Eventually accepts appends again").until(() -> flow.tryAcquire().isRight());
  }
}
