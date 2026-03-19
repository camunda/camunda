/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongPollingDisconnectInterceptorTest {

  private LongPollingDisconnectInterceptor interceptor;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private ServletOutputStream outputStream;
  private CompletableFuture<Object> future;

  @BeforeEach
  void setUp() throws Exception {
    interceptor = new LongPollingDisconnectInterceptor(500);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    outputStream = mock(ServletOutputStream.class);
    future = new CompletableFuture<>();

    when(response.getOutputStream()).thenReturn(outputStream);
    when(request.isAsyncStarted()).thenReturn(true);
    when(request.getAttribute(LongPollingDisconnectInterceptor.ACTIVATE_JOBS_FUTURE_ATTR))
        .thenReturn(future);
  }

  @AfterEach
  void tearDown() {
    interceptor.destroy();
    if (!future.isDone()) {
      future.complete(null);
    }
  }

  @Test
  void shouldNotProbeWhenNoFutureAttribute() {
    when(request.getAttribute(LongPollingDisconnectInterceptor.ACTIVATE_JOBS_FUTURE_ATTR))
        .thenReturn(null);

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());

    // Give the scheduler time to fire if it was incorrectly started, then verify nothing happened
    await()
        .during(Duration.ofMillis(600))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> verify(outputStream, never()).write(anyInt()));
  }

  @Test
  void shouldNotProbeWhenAsyncNotStarted() {
    when(request.isAsyncStarted()).thenReturn(false);

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());

    await()
        .during(Duration.ofMillis(600))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> verify(outputStream, never()).write(anyInt()));
  }

  @Test
  void shouldSetJsonContentTypeBeforeProbing() {
    interceptor.afterConcurrentHandlingStarted(request, response, new Object());

    verify(response).setContentType("application/json");
  }

  @Test
  void shouldProbeConnectionPeriodically() {
    interceptor.afterConcurrentHandlingStarted(request, response, new Object());

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> verify(outputStream, atLeast(1)).write(' '));
  }

  @Test
  void shouldCancelFutureWhenProbeFails() throws Exception {
    doThrow(new IOException("Broken pipe")).when(outputStream).write(anyInt());

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(future.isCancelled()).isTrue());
  }

  @Test
  void shouldStopProbingWhenFutureCompletes() throws Exception {
    final AtomicInteger writeCount = new AtomicInteger(0);
    doAnswer(
            inv -> {
              writeCount.incrementAndGet();
              return null;
            })
        .when(outputStream)
        .write(anyInt());

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());

    // Wait for at least one probe
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(writeCount.get()).isGreaterThanOrEqualTo(1));

    // Complete the future, then capture the count
    future.complete(null);
    await().atMost(Duration.ofMillis(200)).until(() -> future.isDone());

    final int countAfterComplete = writeCount.get();

    // Verify no more writes happen
    await()
        .during(Duration.ofMillis(600))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(writeCount.get()).isEqualTo(countAfterComplete));
  }

  @Test
  void shouldNotWriteDuringResponseSerialization() throws Exception {
    final CountDownLatch probeStarted = new CountDownLatch(1);
    final CountDownLatch probeCanProceed = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              probeStarted.countDown();
              probeCanProceed.await();
              return null;
            })
        .when(outputStream)
        .write(anyInt());

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());

    // Wait for a probe to start (holding the lock)
    probeStarted.await();

    // Complete the future — the lock ensures this waits for the probe to finish
    final CompletableFuture<Void> completionDone = new CompletableFuture<>();
    future.whenComplete((r, ex) -> completionDone.complete(null));
    future.complete(null);

    // Release the probe so the lock is freed and completion can proceed
    probeCanProceed.countDown();

    await().atMost(Duration.ofSeconds(2)).until(() -> completionDone.isDone());

    // After completion, no more writes should occur
    final AtomicInteger postCompletionWrites = new AtomicInteger(0);
    doAnswer(
            inv -> {
              postCompletionWrites.incrementAndGet();
              return null;
            })
        .when(outputStream)
        .write(anyInt());

    await()
        .during(Duration.ofMillis(600))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(postCompletionWrites.get()).isZero());
  }
}
