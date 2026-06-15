/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.AnalyticsAttributes.EVENT_SAMPLE_RATE;
import static io.camunda.exporter.analytics.AnalyticsAttributes.EVENT_TIME_MAX;
import static io.camunda.exporter.analytics.AnalyticsAttributes.EVENT_TIME_MIN;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_END;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_START;
import static io.camunda.exporter.analytics.AnalyticsAttributes.METRIC_EXPORT_WINDOW;
import static io.camunda.exporter.analytics.AnalyticsAttributes.METRIC_SEQUENCE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.exporter.analytics.sampling.HashSampler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
    manager.close();
  }

  /** Real OTLP transport to a refused port doesn't block or throw. */
  @Test
  @Timeout(10)
  void shouldHandleUnreachableEndpoint() {
    // given — localhost:1 gives an immediate connection refused (fast, no TCP timeout)
    final var manager =
        new OtelSdkManager()
            .initialize(
                new AnalyticsExporterConfig().setEndpoint("http://localhost:1"),
                AnalyticsExporterContext.create("test-license", "test-cluster", 1),
                new AnalyticsExporterMetadata());

    // when — @Timeout is the assertion: if logEvent blocks, we die
    for (int i = 0; i < 10; i++) {
      manager.logEvent("test", 0L, log -> {});
    }

    manager.close();
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
    manager.close();

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
    manager.close();

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
    manager.close();

    // then — post-recovery events arrived
    assertThat(receivedEventNames).contains("after_recovery");
  }

  /** Each logEvent() call increments the sequence number by one. */
  @Test
  void shouldIncrementSequenceNumberWithEachLogEvent() {
    // given
    final var received = new CopyOnWriteArrayList<LogRecordData>();
    final var manager =
        initManager(
            logs -> {
              received.addAll(logs);
              return CompletableResultCode.ofSuccess();
            },
            2048,
            512);

    // when
    manager.logEvent("test", 0L, log -> {});
    manager.logEvent("test", 0L, log -> {});
    manager.logEvent("test", 0L, log -> {});
    manager.close();

    // then
    assertThat(received).hasSize(3);
    assertThat(received.get(0).getAttributes().get(AnalyticsAttributes.EVENT_SEQUENCE_NUMBER))
        .isEqualTo(1L);
    assertThat(received.get(1).getAttributes().get(AnalyticsAttributes.EVENT_SEQUENCE_NUMBER))
        .isEqualTo(2L);
    assertThat(received.get(2).getAttributes().get(AnalyticsAttributes.EVENT_SEQUENCE_NUMBER))
        .isEqualTo(3L);
  }

  /** The sequence number continues from the initial value provided at initialization. */
  @Test
  void shouldInitializeSequenceNumberFromGivenValue() {
    // given — manager initialized with sequence number 5
    final var received = new CopyOnWriteArrayList<LogRecordData>();
    final var manager =
        new OtelSdkManager() {
          @Override
          protected LogRecordExporter createLogExporter(
              final AnalyticsExporterConfig cfg, final AnalyticsExporterContext context) {
            return exporterFrom(
                logs -> {
                  received.addAll(logs);
                  return CompletableResultCode.ofSuccess();
                });
          }
        }.initialize(
            new AnalyticsExporterConfig().setPushInterval("PT0.1S"),
            AnalyticsExporterContext.create("test-license", "test-cluster", 1),
            new AnalyticsExporterMetadata(5L, 0));

    // when
    manager.logEvent("test", 0L, log -> {});
    manager.close();

    // then — sequence continues from 5, so first event gets 6
    assertThat(received)
        .singleElement()
        .extracting(log -> log.getAttributes().get(AnalyticsAttributes.EVENT_SEQUENCE_NUMBER))
        .isEqualTo(6L);
  }

  /** logEvent() and shutdown() are safe to call after shutdown (idempotent, no NPE). */
  @Test
  void shouldHandlePostShutdownCallsGracefully() {
    // given
    final var manager = initManager(logs -> CompletableResultCode.ofSuccess(), 2048, 512);
    manager.close();

    // when / then
    assertThatCode(() -> manager.logEvent("post_shutdown", 0L, log -> {}))
        .doesNotThrowAnyException();
    assertThatCode(manager::close).doesNotThrowAnyException();
  }

  // -- helpers --

  private static OtelSdkManager initManager(
      final Function<Collection<LogRecordData>, CompletableResultCode> exportFn,
      final int maxQueueSize,
      final int maxBatchSize) {
    return new OtelSdkManager() {
      @Override
      protected LogRecordExporter createLogExporter(
          final AnalyticsExporterConfig cfg, final AnalyticsExporterContext context) {
        return exporterFrom(exportFn);
      }
    }.initialize(
        new AnalyticsExporterConfig()
            .setMaxQueueSize(maxQueueSize)
            .setMaxBatchSize(maxBatchSize)
            .setPushInterval("PT0.1S"),
        AnalyticsExporterContext.create("test-license", "test-cluster", 1),
        new AnalyticsExporterMetadata());
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

  @Nested
  class Sampling {

    private InMemoryLogRecordExporter logExporter;
    private OtelSdkManager manager;

    @BeforeEach
    void setUp() {
      logExporter = InMemoryLogRecordExporter.create();
      manager = TestOtelSdkManager.inMemory(logExporter);
    }

    @Test
    void shouldSkipEventWhenSamplingDecisionRejects() {
      // given — rate 0.0 rejects all positions
      // when
      manager.logEvent("test", 42L, 0.0, log -> {});

      // then — no log record emitted, sequence number not incremented
      assertThat(logExporter.getFinishedLogRecordItems()).isEmpty();
    }

    @Test
    void shouldEmitEventWhenSamplingDecisionAccepts() {
      // given — rate 1.0 accepts all positions
      // when
      manager.logEvent("test", 42L, HashSampler.MAX_SAMPLE_RATE, log -> {});

      // then
      assertThat(logExporter.getFinishedLogRecordItems()).hasSize(1);
    }

    @Test
    void shouldNotIncrementSequenceNumberWhenEventIsDropped() {
      // given — rate 0.0 drops everything
      manager.logEvent("test", 1L, 0.0, log -> {});
      manager.logEvent("test", 2L, 0.0, log -> {});

      // when — emit one with full rate
      manager.logEvent("test", 3L, HashSampler.MAX_SAMPLE_RATE, log -> {});

      // then — sequence number is 1 (not 3), proving drops don't consume slots
      assertThat(logExporter.getFinishedLogRecordItems())
          .singleElement()
          .extracting(log -> log.getAttributes().get(AnalyticsAttributes.EVENT_SEQUENCE_NUMBER))
          .isEqualTo(1L);
    }

    @Test
    void shouldEmitSampleRateAttributeWhenRateBelowMax() {
      // given — find a position that passes at rate 0.5
      long passingPosition = -1L;
      for (long pos = 0; pos < 1000; pos++) {
        if (HashSampler.shouldSample(pos, 0.5)) {
          passingPosition = pos;
          break;
        }
      }
      assertThat(passingPosition).isGreaterThanOrEqualTo(0L);

      // when
      manager.logEvent("test", passingPosition, 0.5, log -> {});

      // then — sample_rate attribute is set
      assertThat(logExporter.getFinishedLogRecordItems())
          .singleElement()
          .extracting(log -> log.getAttributes().get(EVENT_SAMPLE_RATE))
          .isEqualTo(0.5);
    }

    @Test
    void shouldNotEmitSampleRateAttributeWhenRateIsMax() {
      // when — default logEvent (no rate param)
      manager.logEvent("test", 42L, log -> {});

      // then — sample_rate attribute is absent
      assertThat(logExporter.getFinishedLogRecordItems())
          .singleElement()
          .satisfies(log -> assertThat(log.getAttributes().get(EVENT_SAMPLE_RATE)).isNull());
    }

    @Test
    void shouldApplyMinOfDefaultAndHandlerRate() {
      // given — default rate 0.5 via config, handler rate 0.8 → effective is 0.5
      final var customLogExporter = InMemoryLogRecordExporter.create();
      final var customManager =
          TestOtelSdkManager.inMemory(
              customLogExporter, new AnalyticsExporterConfig().setSamplingRate(0.5));

      // find a position that passes at 0.5
      long passingPosition = -1L;
      for (long pos = 0; pos < 1000; pos++) {
        if (HashSampler.shouldSample(pos, 0.5)) {
          passingPosition = pos;
          break;
        }
      }
      assertThat(passingPosition).isGreaterThanOrEqualTo(0L);

      // when — handler requests 0.8, but default is 0.5
      customManager.logEvent("test", passingPosition, 0.8, log -> {});

      // then — effective rate 0.5 is used (attribute reflects the min)
      assertThat(customLogExporter.getFinishedLogRecordItems())
          .singleElement()
          .extracting(log -> log.getAttributes().get(EVENT_SAMPLE_RATE))
          .isEqualTo(0.5);
    }
  }

  @Nested
  class MetricRecording {

    private InMemoryMetricReader metricReader;
    private OtelSdkManager manager;

    @BeforeEach
    void setUp() {
      metricReader = InMemoryMetricReader.create();
      manager =
          TestOtelSdkManager.inMemoryWithMetrics(InMemoryLogRecordExporter.create(), metricReader);
    }

    @Test
    void shouldRecordMetricByName() {
      // when
      manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());

      // then
      assertThat(findMetric(metricReader.collectAllMetrics(), "test.counter")).isPresent();
    }

    @Test
    void shouldReuseCounterForSameName() {
      // when
      manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());
      manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());

      // then
      assertThat(findMetric(metricReader.collectAllMetrics(), "test.counter"))
          .hasValueSatisfying(
              metric -> {
                final long total =
                    metric.getLongSumData().getPoints().stream()
                        .mapToLong(LongPointData::getValue)
                        .sum();
                assertThat(total).isEqualTo(2);
              });
    }

    @Test
    void shouldCreateSeparateCountersForDifferentNames() {
      // when
      manager.incrementMetric("counter.a", 100L, 1000L, Attributes.empty());
      manager.incrementMetric("counter.b", 200L, 2000L, Attributes.empty());

      // then
      final var metrics = metricReader.collectAllMetrics();
      assertThat(findMetric(metrics, "counter.a")).isPresent();
      assertThat(findMetric(metrics, "counter.b")).isPresent();
    }

    @Test
    void shouldPreserveDimensionAttributes() {
      // given
      final var attrs =
          Attributes.of(
              AttributeKey.stringKey("process.id"), "order", AttributeKey.longKey("version"), 3L);

      // when
      manager.incrementMetric("test.counter", 100L, 1000L, attrs);

      // then
      assertThat(findMetric(metricReader.collectAllMetrics(), "test.counter"))
          .hasValueSatisfying(
              metric ->
                  assertThat(metric.getLongSumData().getPoints())
                      .first()
                      .satisfies(
                          point -> {
                            assertThat(
                                    point.getAttributes().get(AttributeKey.stringKey("process.id")))
                                .isEqualTo("order");
                            assertThat(point.getAttributes().get(AttributeKey.longKey("version")))
                                .isEqualTo(3L);
                          }));
    }

    @Test
    void shouldEmitExportWindowGaugeWithMetadata() {
      // given
      manager.incrementMetric("test.counter", 100L, 5000L, Attributes.empty());
      manager.incrementMetric("test.counter", 200L, 6000L, Attributes.empty());

      // when
      final var metrics = metricReader.collectAllMetrics();

      // then
      assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
          .isPresent()
          .hasValueSatisfying(
              metric ->
                  assertThat(metric.getLongGaugeData().getPoints())
                      .first()
                      .satisfies(
                          point -> {
                            assertThat(point.getValue()).isEqualTo(2);
                            final var pointAttrs = point.getAttributes();
                            assertThat(pointAttrs.get(METRIC_SEQUENCE_NUMBER)).isEqualTo(1L);
                            assertThat(pointAttrs.get(LOG_POSITION_START)).isEqualTo(100L);
                            assertThat(pointAttrs.get(LOG_POSITION_END)).isEqualTo(200L);
                            assertThat(pointAttrs.get(EVENT_TIME_MIN)).isEqualTo(5000L);
                            assertThat(pointAttrs.get(EVENT_TIME_MAX)).isEqualTo(6000L);
                          }));
    }

    @Test
    void shouldIncrementFlushSequenceAcrossCollections() {
      // given — two collection cycles
      manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());
      metricReader.collectAllMetrics(); // flush_sequence = 1

      manager.incrementMetric("test.counter", 200L, 2000L, Attributes.empty());

      // when
      final var metrics = metricReader.collectAllMetrics();

      // then
      assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
          .isPresent()
          .hasValueSatisfying(
              metric ->
                  assertThat(metric.getLongGaugeData().getPoints())
                      .first()
                      .satisfies(
                          point ->
                              assertThat(point.getAttributes().get(METRIC_SEQUENCE_NUMBER))
                                  .isEqualTo(2L)));
    }

    @Test
    void shouldResetWindowAfterCollection() {
      // given
      manager.incrementMetric("test.counter", 100L, 5000L, Attributes.empty());
      metricReader.collectAllMetrics(); // resets window

      manager.incrementMetric("test.counter", 300L, 8000L, Attributes.empty());

      // when
      final var metrics = metricReader.collectAllMetrics();

      // then — should reflect only the second event
      assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
          .isPresent()
          .hasValueSatisfying(
              metric ->
                  assertThat(metric.getLongGaugeData().getPoints())
                      .first()
                      .satisfies(
                          point -> {
                            assertThat(point.getValue()).isEqualTo(1);
                            final var pointAttrs = point.getAttributes();
                            assertThat(pointAttrs.get(LOG_POSITION_START)).isEqualTo(300L);
                            assertThat(pointAttrs.get(LOG_POSITION_END)).isEqualTo(300L);
                            assertThat(pointAttrs.get(EVENT_TIME_MIN)).isEqualTo(8000L);
                            assertThat(pointAttrs.get(EVENT_TIME_MAX)).isEqualTo(8000L);
                          }));
    }

    @Test
    void shouldSkipExportWindowGaugeWhenNoEvents() {
      // when — no incrementMetric calls
      final var metrics = metricReader.collectAllMetrics();

      // then — gauge has no data points
      assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
          .satisfiesAnyOf(
              opt -> assertThat(opt).isEmpty(),
              opt ->
                  assertThat(opt)
                      .hasValueSatisfying(
                          metric -> assertThat(metric.getLongGaugeData().getPoints()).isEmpty()));
    }

    @Test
    void shouldNotIncrementMetricSequenceWhenWindowIsEmpty() {
      // when — first collection happens with no events, then a real increment, then collection
      metricReader.collectAllMetrics(); // empty window — should not consume a sequence number
      manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());
      final var metrics = metricReader.collectAllMetrics();

      // then — sequence number is 1 (not 2), proving the empty window did not consume a slot
      assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
          .isPresent()
          .hasValueSatisfying(
              metric ->
                  assertThat(metric.getLongGaugeData().getPoints())
                      .first()
                      .satisfies(
                          point ->
                              assertThat(point.getAttributes().get(METRIC_SEQUENCE_NUMBER))
                                  .isEqualTo(1L)));
    }

    private Optional<MetricData> findMetric(
        final Collection<MetricData> metrics, final String name) {
      return metrics.stream().filter(m -> m.getName().equals(name)).findFirst();
    }
  }
}
