/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.zeebe.DataReadMeter.ReadQuery;
import io.camunda.zeebe.StarterLatencyMetricsDoc.StarterMetricKeyNames;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class DataReadMeterTest {

  private SimpleMeterRegistry meterRegistry;
  private TestScheduledExecutor executor;
  private DataReadMeter meter;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    executor = new TestScheduledExecutor();
    meter = new DataReadMeter(meterRegistry, executor);
  }

  @AfterEach
  void tearDown() {
    if (meter != null) {
      meter.close();
    }
    meterRegistry.close();
  }

  @Test
  void shouldRecordLatencyForSuccessfulQuery() {
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send()).thenReturn(TestCamundaFuture.completed(null));

    final ReadQuery query = new ReadQuery("readSuccess", Duration.ofMillis(5), c -> commandStep);

    meter.start(mock(CamundaClient.class), List.of(query));

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(StarterLatencyMetricsDoc.READ_BENCHMARK.getName())
                            .tag(StarterMetricKeyNames.QUERY_NAME.asString(), "readSuccess")
                            .timer()
                            .count())
                    .isGreaterThanOrEqualTo(1));

    final Timer timer =
        meterRegistry
            .get(StarterLatencyMetricsDoc.READ_BENCHMARK.getName())
            .tag(StarterMetricKeyNames.QUERY_NAME.asString(), "readSuccess")
            .timer();

    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
    meter.close();
    meter = null;
  }

  @Test
  void shouldRecordLatencyEvenWhenQueryFails() {
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send())
        .thenReturn(TestCamundaFuture.failed(new IllegalStateException("boom")));

    final ReadQuery query = new ReadQuery("readFailure", Duration.ofMillis(5), c -> commandStep);

    assertThatCode(() -> meter.start(mock(CamundaClient.class), List.of(query)))
        .doesNotThrowAnyException();

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () ->
                assertThat(
                        meterRegistry
                            .get(StarterLatencyMetricsDoc.READ_BENCHMARK.getName())
                            .tag(StarterMetricKeyNames.QUERY_NAME.asString(), "readFailure")
                            .timer()
                            .count())
                    .isGreaterThanOrEqualTo(2));

    meter.close();
    meter = null;
  }

  @Test
  void shouldCancelScheduledTasksOnClose() {
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send()).thenReturn(TestCamundaFuture.completed(null));

    final ReadQuery first = new ReadQuery("first", Duration.ofMillis(5), c -> commandStep);
    final ReadQuery second = new ReadQuery("second", Duration.ofMillis(5), c -> commandStep);

    meter.start(mock(CamundaClient.class), List.of(first, second));

    meter.close();

    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(() -> assertThat(executor.isShutdown()).isTrue());
    await()
        .atMost(Duration.ofMillis(500))
        .untilAsserted(
            () ->
                assertThat(executor.futures())
                    .hasSize(2)
                    .allSatisfy(future -> assertThat(future.isCancelled()).isTrue()));
  }

  private static final class TestCamundaFuture<T> extends CompletableFuture<T>
      implements CamundaFuture<T> {

    static <T> TestCamundaFuture<T> completed(final T value) {
      final TestCamundaFuture<T> future = new TestCamundaFuture<>();
      future.complete(value);
      return future;
    }

    static <T> TestCamundaFuture<T> failed(final Throwable error) {
      final TestCamundaFuture<T> future = new TestCamundaFuture<>();
      future.completeExceptionally(error);
      return future;
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning, final Throwable cause) {
      return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public T join(final long timeout, final TimeUnit unit) {
      return super.join();
    }
  }

  private static final class TestScheduledExecutor extends ScheduledThreadPoolExecutor {

    private final List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>();

    TestScheduledExecutor() {
      super(1);
      setRemoveOnCancelPolicy(true);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        final Runnable command, final long initialDelay, final long delay, final TimeUnit unit) {
      final ScheduledFuture<?> future =
          super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
      futures.add(future);
      return future;
    }

    List<ScheduledFuture<?>> futures() {
      return futures;
    }
  }
}
