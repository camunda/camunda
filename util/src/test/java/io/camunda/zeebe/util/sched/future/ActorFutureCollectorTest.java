/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ActorFutureCollectorTest {

  private ActorFuture<String> future1;
  private ActorFuture<String> future2;

  private ActorFuture<List<String>> aggregatedFuture;

  @BeforeEach
  public void setUp() {
    final var concurrencyControl = new TestConcurrencyControl();

    future1 = concurrencyControl.createFuture();
    future2 = concurrencyControl.createFuture();

    aggregatedFuture =
        Stream.of(future1, future2).collect(new ActorFutureCollector<>(concurrencyControl));
  }

  @Test
  public void shouldAggregateResults() {
    // when
    future1.complete("result1");
    future2.complete("result2");

    // then
    assertThat(aggregatedFuture.join()).containsExactly("result1", "result2");
  }

  @Test
  public void shouldCompleteWithException() {
    // given
    final var testException = new Exception("future2 threw exception");

    // when
    future1.complete("result1");
    future2.completeExceptionally(testException);

    // then
    assertThatThrownBy(() -> aggregatedFuture.join())
        .getCause()
        .hasSuppressedException(testException);
  }

  @Test
  public void shouldAggregateExceptions() {
    // given
    final var testException1 = new Exception("future1 threw exception");
    final var testException2 = new Exception("future2 threw exception");

    // when
    future1.completeExceptionally(testException1);
    future2.completeExceptionally(testException2);

    // then
    assertThatThrownBy(() -> aggregatedFuture.join())
        .getCause()
        .hasSuppressedException(testException1)
        .hasSuppressedException(testException2);
  }
}
