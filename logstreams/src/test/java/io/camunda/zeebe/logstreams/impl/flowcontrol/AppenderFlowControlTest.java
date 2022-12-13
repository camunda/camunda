/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import java.time.Duration;
import java.util.LinkedList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;

@Execution(ExecutionMode.CONCURRENT)
final class AppenderFlowControlTest {
  @Test
  void callsErrorHandlerOnWriteError() {
    // given
    final var errorHandler = Mockito.mock(AppendErrorHandler.class);
    final var flow = new AppenderFlowControl(errorHandler, 1);
    final var error = new RuntimeException();
    // when
    final var inFlight = flow.tryAcquire();
    inFlight.onWriteError(error);
    // then
    Mockito.verify(errorHandler).onWriteError(error);
  }

  @Test
  void callsErrorHandlerOnCommitError() {
    // given
    final var errorHandler = Mockito.mock(AppendErrorHandler.class);
    final var flow = new AppenderFlowControl(errorHandler, 1);
    final var error = new RuntimeException();
    // when
    final var inFlight = flow.tryAcquire();
    inFlight.onCommitError(0, error);
    // then
    Mockito.verify(errorHandler).onCommitError(error);
  }

  @Test
  void eventuallyRejects() {
    // given
    final var errorHandler = Mockito.mock(AppendErrorHandler.class);
    final var flow = new AppenderFlowControl(errorHandler, 1);

    // when - then
    Awaitility.await("Rejects new appends")
        .pollInSameThread()
        .pollInterval(Duration.ZERO)
        .until(() -> flow.tryAcquire() == null);
  }

  @Test
  void recoversWhenCompletingAppends() {
    // given
    final var errorHandler = Mockito.mock(AppendErrorHandler.class);
    final var flow = new AppenderFlowControl(errorHandler, 1);
    // when
    boolean rejecting = false;
    final var inFlight = new LinkedList<AppendInFlight>();
    do {
      final var result = flow.tryAcquire();
      if (result == null) {
        rejecting = true;
      } else {
        inFlight.push(result);
      }
    } while (!rejecting);
    inFlight.forEach(append -> append.onCommit(1));

    // then
    Awaitility.await("Eventually accepts appends again").until(() -> flow.tryAcquire() != null);
  }
}
