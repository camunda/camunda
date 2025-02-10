/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter.Listener;
import com.netflix.concurrency.limits.limit.SettableLimit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    final var flow =
        new AppenderFlowControl(errorHandler, new AppenderMetrics(new SimpleMeterRegistry()));
    final var error = new RuntimeException();
    // when
    final var inFlight = flow.tryAcquire().orElseThrow();
    inFlight.onWriteError(error);
    // then
    Mockito.verify(errorHandler).onWriteError(error);
  }

  @Test
  void callsErrorHandlerOnCommitError() {
    // given
    final var errorHandler = Mockito.mock(AppendErrorHandler.class);
    final var flow =
        new AppenderFlowControl(errorHandler, new AppenderMetrics(new SimpleMeterRegistry()));
    final var error = new RuntimeException();
    // when
    final var inFlight = flow.tryAcquire().orElseThrow();
    inFlight.onCommitError(0, error);
    // then
    Mockito.verify(errorHandler).onCommitError(error);
  }

  @Test
  void eventuallyRejects() {
    // given
    final var errorHandler = Mockito.mock(AppendErrorHandler.class);
    final var flow =
        new AppenderFlowControl(errorHandler, new AppenderMetrics(new SimpleMeterRegistry()));

    // when - then
    Awaitility.await("Rejects new appends")
        .pollInSameThread()
        .pollInterval(Duration.ZERO)
        .until(() -> flow.tryAcquire().isEmpty());
  }

  @Test
  void recoversWhenCompletingAppends() {
    // given
    final var errorHandler = Mockito.mock(AppendErrorHandler.class);
    final var flow =
        new AppenderFlowControl(errorHandler, new AppenderMetrics(new SimpleMeterRegistry()));
    // when
    boolean rejecting = false;
    final var inFlight = new LinkedList<InFlightAppend>();
    do {
      final var result = flow.tryAcquire();
      if (result.isEmpty()) {
        rejecting = true;
      } else {
        inFlight.push(result.get());
      }
    } while (!rejecting);
    inFlight.forEach(append -> append.onCommit(1));

    // then
    Awaitility.await("Eventually accepts appends again").until(() -> flow.tryAcquire().isPresent());
  }

  @Test
  void shouldNotAllowInFlightHigherThanLimit() throws InterruptedException {
    // given
    final int numThreads = 3000;
    final int poolSize = 300;
    final int limit = 100;
    final Limit myLimit = new SettableLimit(limit);
    final AppenderMetrics appenderMetrics = new AppenderMetrics(new SimpleMeterRegistry());
    appenderMetrics.setInflightLimit(limit);
    final AppendLimiter myRateLimiter =
        AppendLimiter.builder().limit(myLimit).metrics(appenderMetrics).build();
    final ExecutorService threadPool = Executors.newFixedThreadPool(poolSize);
    final Optional<Listener>[] listeners = new Optional[numThreads];

    final Collection<Callable<Object>> tasks =
        IntStream.range(0, numThreads)
            .mapToObj(
                i ->
                    (Callable<Object>)
                        () -> {
                          final int sleepTime = ThreadLocalRandom.current().nextInt(limit);
                          listeners[i] = myRateLimiter.acquire(null);
                          Thread.sleep(sleepTime);
                          assertThat(myRateLimiter.getInflight()).isLessThanOrEqualTo(limit);
                          listeners[i].get().onSuccess();
                          return null;
                        })
            .collect(Collectors.toList());
    try {
      threadPool.invokeAll(tasks);
    } finally {
      // when
      threadPool.shutdown();
    }

    // then
    assertThat(myRateLimiter.getInflight()).isEqualTo(0);
  }
}
