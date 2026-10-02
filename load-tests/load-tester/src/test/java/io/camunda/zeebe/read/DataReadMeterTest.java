/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc;
import io.camunda.zeebe.metrics.StarterLatencyMetricsDoc.StarterLatencyMetricKeyNames;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.read.DataReadMeter.ReadQuery;
import io.camunda.zeebe.read.DataReadMeter.ReadQueryContext;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class DataReadMeterTest {

  private SimpleMeterRegistry meterRegistry;
  private ManualScheduledExecutor executor;
  private DataReadMeter meter;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    executor = new ManualScheduledExecutor();
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
    // given
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send()).thenReturn(TestCamundaFuture.completed(null));
    final ReadQuery query =
        new ReadQuery("readSuccess", Duration.ofMillis(5), (client, context) -> commandStep);
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();

    // when the query runs and its completion callback runs
    executor.runTasks(2);

    // then
    final Timer timer = timer("readSuccess");
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isPositive();
  }

  @Test
  void shouldDropPendingExecutionsOnClose() {
    // given
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send()).thenReturn(TestCamundaFuture.completed(null));
    final ReadQuery first =
        new ReadQuery("first", Duration.ofHours(1), (client, context) -> commandStep);
    final ReadQuery second =
        new ReadQuery("second", Duration.ofHours(1), (client, context) -> commandStep);
    meter =
        new DataReadMeter(
            meterRegistry, executor, mock(CamundaClient.class), List.of(first, second));
    meter.start();
    assertThat(executor.pending()).isEqualTo(2);

    // when
    meter.close();

    // then
    assertThat(executor.isShutdown()).isTrue();
    assertThat(executor.pending()).isZero();
  }

  @Test
  void shouldNotScheduleAgainWhenQueryCompletesAfterClose() {
    // given
    final var commandStep = mock(FinalCommandStep.class);
    final var future = new TestCamundaFuture<Void>();
    when(commandStep.send()).thenReturn(future);
    final ReadQuery query =
        new ReadQuery("late", Duration.ofMillis(5), (client, context) -> commandStep);
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();
    executor.runTasks(1);

    // when
    meter.close();
    future.complete(null);

    // then
    assertThat(executor.pending()).isZero();
  }

  @Test
  void shouldKeepSchedulingAfterFailedQuery() {
    // given
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send())
        .thenReturn(TestCamundaFuture.failed(new IllegalStateException("boom")))
        .thenReturn(TestCamundaFuture.completed(null));
    final ReadQuery query =
        new ReadQuery("readFailing", Duration.ofMillis(5), (client, context) -> commandStep);
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();

    // when the failing run, its callback, the next run and its callback execute
    executor.runTasks(4);

    // then the failed and the successful run are recorded separately
    assertThat(timer("readFailing").count()).isEqualTo(1);
    assertThat(failureTimer("readFailing", "IllegalStateException").count()).isEqualTo(1);
  }

  @Test
  void shouldRecordFailureWhenQueryCannotBeSent() {
    // given
    final ReadQuery query =
        new ReadQuery(
            "readThrowing",
            Duration.ofMillis(5),
            (client, context) -> {
              throw new IllegalArgumentException("boom");
            });
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();

    // when
    executor.runTasks(1);

    // then the failure is counted and the query is scheduled again
    assertThat(failureTimer("readThrowing", "IllegalArgumentException").count()).isEqualTo(1);
    assertThat(timer("readThrowing").count()).isZero();
    assertThat(executor.pending()).isEqualTo(1);
  }

  @Test
  void shouldTagFailureWithStatusOfTheError() {
    // given
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send())
        .thenReturn(TestCamundaFuture.failed(new ClientHttpException(503, "unavailable")));
    final ReadQuery query =
        new ReadQuery("readHttp", Duration.ofMillis(5), (client, context) -> commandStep);
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();

    // when
    executor.runTasks(2);

    // then
    assertThat(failureTimer("readHttp", "http_503").count()).isEqualTo(1);
  }

  @Test
  void shouldNotBlockOtherQueriesWhileOneQueryIsSlow() {
    // given
    final var slowStep = mock(FinalCommandStep.class);
    final var slowCalls = new AtomicInteger();
    when(slowStep.send())
        .thenAnswer(
            invocation -> {
              slowCalls.incrementAndGet();
              return new TestCamundaFuture<Void>();
            });
    final var fastStep = mock(FinalCommandStep.class);
    when(fastStep.send()).thenAnswer(invocation -> TestCamundaFuture.completed(null));
    final ReadQuery slow =
        new ReadQuery("slow", Duration.ofMillis(5), (client, context) -> slowStep);
    final ReadQuery fast =
        new ReadQuery("fast", Duration.ofMillis(5), (client, context) -> fastStep);
    meter =
        new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(slow, fast));
    meter.start();

    // when the single executor thread keeps running tasks while the slow query never answers
    executor.runTasks(20);

    // then
    assertThat(timer("fast").count()).isGreaterThanOrEqualTo(5);
    assertThat(slowCalls).hasValue(1);
    assertThat(timer("slow").count()).isZero();
  }

  @Test
  void shouldScheduleNextExecutionOnlyAfterPreviousCompleted() {
    // given
    final var commandStep = mock(FinalCommandStep.class);
    final var firstFuture = new TestCamundaFuture<Void>();
    final var calls = new AtomicInteger();
    when(commandStep.send())
        .thenAnswer(
            invocation ->
                calls.incrementAndGet() == 1 ? firstFuture : new TestCamundaFuture<Void>());
    final ReadQuery query =
        new ReadQuery("readOnce", Duration.ofMillis(5), (client, context) -> commandStep);
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();

    // when the first run is sent but not answered
    executor.runTasks(1);

    // then nothing else is scheduled
    assertThat(calls).hasValue(1);
    assertThat(executor.pending()).isZero();

    // when the answer arrives
    firstFuture.complete(null);

    // then the completion callback is queued on the executor, not run on the completing thread
    assertThat(executor.pending()).isEqualTo(1);
    assertThat(timer("readOnce").count()).isZero();

    // when the callback runs
    executor.runTasks(1);

    // then the latency is recorded and the next run is queued
    assertThat(timer("readOnce").count()).isEqualTo(1);
    assertThat(calls).hasValue(1);
    assertThat(executor.pending()).isEqualTo(1);

    // when the next run executes
    executor.runTasks(1);

    // then
    assertThat(calls).hasValue(2);
  }

  @Test
  void shouldNotNestExecutionsOnTheStack() {
    // given a query that is answered before the callback is registered
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send()).thenAnswer(invocation -> TestCamundaFuture.completed(null));
    final ReadQuery query =
        new ReadQuery("readOften", Duration.ZERO, (client, context) -> commandStep);
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();

    // when the run executes
    executor.runTasks(1);

    // then the callback is queued instead of running inline
    assertThat(executor.pending()).isEqualTo(1);
    assertThat(timer("readOften").count()).isZero();

    // when the callback executes
    executor.runTasks(1);

    // then the next run is queued instead of running inline
    assertThat(executor.pending()).isEqualTo(1);
    assertThat(timer("readOften").count()).isEqualTo(1);
  }

  @Test
  void shouldNotCountExecutorQueueDelayAsLatency() {
    // given
    final var commandStep = mock(FinalCommandStep.class);
    final var future = new TestCamundaFuture<Void>();
    when(commandStep.send()).thenReturn(future);
    final ReadQuery query =
        new ReadQuery("queued", Duration.ofMillis(5), (client, context) -> commandStep);
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();
    executor.runTasks(1);

    // when the response arrives but the executor only gets to the callback later
    future.complete(null);
    await().pollDelay(Duration.ofMillis(300)).until(() -> true);
    executor.runTasks(1);

    // then the wait for the executor is not part of the recorded latency
    final Timer timer = timer("queued");
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isLessThan(150);
  }

  @Test
  void shouldKeepOtherFieldsWhenOneFieldIsUpdated() {
    // given
    final var observed = startMeterCapturingContext();
    final Supplier<Pair<String, Object>> businessKey = () -> Pair.of("key", 42L);

    // when
    meter.setContextProcessInstanceKey(1L);
    meter.setContextProcessDefinitionId("definition");
    meter.setContextProcessDefinitionKey(2L);
    meter.setContextBusinessKeySupplier(businessKey);
    executor.runTasks(1);

    // then
    assertThat(observed.get()).isEqualTo(new ReadQueryContext(1L, "definition", 2L, businessKey));
  }

  @Test
  void shouldNotLoseConcurrentContextUpdates() {
    // given
    final var observed = startMeterCapturingContext();
    final int updates = 2_000;
    final Supplier<Pair<String, Object>> businessKey = () -> Pair.of("key", 42L);

    // when every field is updated from its own thread
    CompletableFuture.allOf(
            CompletableFuture.runAsync(
                () ->
                    IntStream.rangeClosed(1, updates).forEach(meter::setContextProcessInstanceKey)),
            CompletableFuture.runAsync(
                () ->
                    IntStream.rangeClosed(1, updates)
                        .forEach(meter::setContextProcessDefinitionKey)),
            CompletableFuture.runAsync(
                () ->
                    IntStream.rangeClosed(1, updates)
                        .forEach(i -> meter.setContextProcessDefinitionId("definition-" + i))),
            CompletableFuture.runAsync(
                () ->
                    IntStream.rangeClosed(1, updates)
                        .forEach(
                            i ->
                                meter.setContextBusinessKeySupplier(
                                    i == updates ? businessKey : () -> null))))
        .join();
    executor.runTasks(1);

    // then no thread overwrote the last value of another field
    assertThat(observed.get())
        .isEqualTo(new ReadQueryContext(updates, "definition-" + updates, updates, businessKey));
  }

  /** Starts a meter whose only query records the context it is called with. */
  private AtomicReference<ReadQueryContext> startMeterCapturingContext() {
    final var commandStep = mock(FinalCommandStep.class);
    when(commandStep.send()).thenAnswer(invocation -> TestCamundaFuture.completed(null));
    final var observed = new AtomicReference<ReadQueryContext>();
    final ReadQuery query =
        new ReadQuery(
            "context",
            Duration.ofMillis(5),
            (client, context) -> {
              observed.set(context);
              return commandStep;
            });
    meter = new DataReadMeter(meterRegistry, executor, mock(CamundaClient.class), List.of(query));
    meter.start();
    return observed;
  }

  private Timer timer(final String queryName) {
    return timer(queryName, "success", "none");
  }

  private Timer failureTimer(final String queryName, final String error) {
    return timer(queryName, "failure", error);
  }

  private Timer timer(final String queryName, final String outcome, final String error) {
    return meterRegistry
        .get(StarterLatencyMetricsDoc.READ_BENCHMARK.getName())
        .tag(StarterLatencyMetricKeyNames.QUERY_NAME.asString(), queryName)
        .tag(StarterMetricKeyNames.OUTCOME.asString(), outcome)
        .tag(StarterMetricKeyNames.ERROR.asString(), error)
        .timer();
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

  /**
   * Queues scheduled and submitted tasks, ignoring their delay, and runs them on the calling thread
   * when the test asks for it. Tasks therefore run one at a time in submission order, like on a
   * single-threaded scheduler.
   */
  private static final class ManualScheduledExecutor extends ScheduledThreadPoolExecutor {

    private final Queue<Runnable> tasks = new ArrayDeque<>();

    ManualScheduledExecutor() {
      super(1);
    }

    @Override
    public ScheduledFuture<?> schedule(
        final Runnable command, final long delay, final TimeUnit unit) {
      if (isShutdown()) {
        throw new RejectedExecutionException("executor is shut down");
      }
      tasks.add(command);
      return null;
    }

    @Override
    public List<Runnable> shutdownNow() {
      tasks.clear();
      return super.shutdownNow();
    }

    void runTasks(final int max) {
      for (int i = 0; i < max && !tasks.isEmpty(); i++) {
        tasks.poll().run();
      }
    }

    int pending() {
      return tasks.size();
    }
  }
}
