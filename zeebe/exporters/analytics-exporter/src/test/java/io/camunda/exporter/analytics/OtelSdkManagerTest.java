/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests the OtelSdkManager contract: logEvent() never blocks, events are delivered end-to-end,
 * pipeline recovers from failures, and shutdown is safe.
 *
 * <p>Uses BatchLogRecordProcessor (the production pipeline) with a swapped transport. Tests that
 * need deterministic failure semantics (no retry) are noted.
 */
class OtelSdkManagerTest {

  /** logEvent() returns immediately even when the export pipeline is completely stalled. */
  @Test
  @Timeout(10)
  void shouldNotBlockWhenQueueIsFullAndDropEvents() {
    // given — tiny queue (4), exporter blocks until we release the latch
    final var exportStarted = new CountDownLatch(1);
    final var releaseLatch = new CountDownLatch(1);
    final var received = new AtomicInteger(0);
    final var manager =
        initManager(
            logs -> {
              received.addAndGet(logs.size());
              exportStarted.countDown();
              try {
                releaseLatch.await();
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return CompletableResultCode.ofSuccess();
            },
            4,
            2);

    // when — flood 100 events; @Timeout kills the test if logEvent ever blocks
    for (int i = 0; i < 100; i++) {
      manager.logEvent("test", 0L, log -> {});
    }

    // then — wait for the exporter to confirm it started, then verify drops
    try {
      exportStarted.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThat(received.get()).isLessThan(100);

    releaseLatch.countDown();
    manager.shutdown();
  }

  /** Real OTLP transport to a refused port doesn't block or throw. */
  @Test
  @Timeout(10)
  void shouldHandleUnreachableEndpoint() {
    // given — localhost:1 gives an immediate connection refused (fast, no TCP timeout)
    final var manager =
        new OtelSdkManager()
            .initialize(
                new AnalyticsExporterConfig().setEnabled(true).setEndpoint("http://localhost:1"),
                "test-cluster",
                1);

    // when — @Timeout is the assertion: if logEvent blocks, we die
    for (int i = 0; i < 10; i++) {
      manager.logEvent("test", 0L, log -> {});
    }

    manager.shutdown();
  }

  /** Events sent via logEvent() arrive at the exporter. */
  @Test
  @Timeout(30)
  void shouldDeliverEventsToExporter() {
    // given
    final var received = new AtomicInteger(0);
    final var manager =
        initManager(
            logs -> {
              received.addAndGet(logs.size());
              return CompletableResultCode.ofSuccess();
            },
            2048,
            512);

    // when
    for (int i = 0; i < 50; i++) {
      manager.logEvent("test", 0L, log -> {});
    }
    manager.shutdown();

    // then — all 50 delivered
    assertThat(received.get()).isEqualTo(50);
  }

  /** Shutdown flushes pending events that haven't been exported yet. */
  @Test
  @Timeout(30)
  void shouldFlushOnShutdown() {
    // given — slow exporter ensures events are still in-flight when shutdown is called
    final var received = new AtomicInteger(0);
    final var manager =
        initManager(
            logs -> {
              received.addAndGet(logs.size());
              try {
                Thread.sleep(50);
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return CompletableResultCode.ofSuccess();
            },
            2048,
            512);

    // when — send events, then immediately shutdown
    for (int i = 0; i < 20; i++) {
      manager.logEvent("test", 0L, log -> {});
    }
    manager.shutdown();

    // then — shutdown waited for the slow exporter to drain
    assertThat(received.get()).isEqualTo(20);
  }

  /**
   * Pipeline drops events during failure and delivers new events after recovery.
   *
   * <p>Note: this test uses a failing exporter where BatchLogRecordProcessor may retry failed
   * batches. We assert that post-recovery events ARE delivered. We don't assert that failure-phase
   * events are absent — with retry, some may eventually succeed. The key contract is: recovery
   * works, new events flow.
   */
  @Test
  @Timeout(30)
  void shouldDeliverEventsAfterRecovery() {
    // given — exporter that fails initially, then recovers
    final var failing = new AtomicBoolean(true);
    final var receivedEventNames = new CopyOnWriteArrayList<String>();
    final var manager =
        initManager(
            logs -> {
              if (failing.get()) {
                return CompletableResultCode.ofFailure();
              }
              logs.forEach(
                  l ->
                      receivedEventNames.add(
                          l.getAttributes().get(AnalyticsAttributes.EVENT_NAME)));
              return CompletableResultCode.ofSuccess();
            },
            2048,
            512);

    // when — send events during failure
    for (int i = 0; i < 10; i++) {
      manager.logEvent("during_failure", 0L, log -> {});
    }

    // recover, then send more and flush
    failing.set(false);
    for (int i = 0; i < 5; i++) {
      manager.logEvent("after_recovery", 0L, log -> {});
    }
    manager.shutdown();

    // then — post-recovery events arrived
    assertThat(receivedEventNames).contains("after_recovery");
  }

  /** logEvent() and shutdown() are safe to call after shutdown (idempotent, no NPE). */
  @Test
  void shouldHandlePostShutdownCallsGracefully() {
    // given
    final var manager = initManager(logs -> CompletableResultCode.ofSuccess(), 2048, 512);
    manager.shutdown();

    // when / then
    assertThatCode(() -> manager.logEvent("post_shutdown", 0L, log -> {}))
        .doesNotThrowAnyException();
    assertThatCode(manager::shutdown).doesNotThrowAnyException();
  }

  // -- helpers --

  private static OtelSdkManager initManager(
      final Function<Collection<LogRecordData>, CompletableResultCode> exportFn,
      final int maxQueueSize,
      final int maxBatchSize) {
    return new OtelSdkManager() {
      @Override
      protected LogRecordExporter createLogExporter(final AnalyticsExporterConfig cfg) {
        return exporterFrom(exportFn);
      }
    }.initialize(
        new AnalyticsExporterConfig()
            .setEnabled(true)
            .setMaxQueueSize(maxQueueSize)
            .setMaxBatchSize(maxBatchSize)
            .setPushInterval("PT0.1S"),
        "test-cluster",
        1);
  }

  private static LogRecordExporter exporterFrom(
      final Function<Collection<LogRecordData>, CompletableResultCode> exportFn) {
    return new LogRecordExporter() {
      @Override
      public CompletableResultCode export(final Collection<LogRecordData> logs) {
        return exportFn.apply(logs);
      }

      @Override
      public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
      }

      @Override
      public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
      }
    };
  }
}
