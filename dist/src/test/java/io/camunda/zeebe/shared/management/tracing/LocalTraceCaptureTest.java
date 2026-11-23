/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class LocalTraceCaptureTest {

  private static final Instant START_INSTANT = Instant.parse("2026-11-23T12:00:00Z");

  @Test
  void shouldDelegateAndCaptureNothingWhenInactive() {
    // given
    final var nanoTime = new AtomicLong(1_000_000_000L);
    try (final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/actuator/traces");
        final var provider = newTracerProvider(capture, Sampler.parentBased(Sampler.alwaysOff()))) {
      final var tracer = provider.get("test");

      // when
      tracer.spanBuilder("untraced").startSpan().end();
      final var remoteSampled =
          SpanContext.createFromRemoteParent(
              "0af7651916cd43dd8448eb211c80319c",
              "b7ad6b7169203331",
              TraceFlags.getSampled(),
              TraceState.getDefault());
      tracer
          .spanBuilder("remote-sampled")
          .setParent(Context.root().with(Span.wrap(remoteSampled)))
          .startSpan()
          .end();

      // then
      assertThat(capture.status().active()).isFalse();
      assertThat(capture.snapshot(null).spans()).isEmpty();
    }
  }

  @Test
  void shouldCaptureOnlySampledParentsInHeaderOnlyMode() {
    // given
    final var nanoTime = new AtomicLong(1_000_000_000L);
    try (final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/actuator/traces");
        final var provider = newTracerProvider(capture, Sampler.parentBased(Sampler.alwaysOff()))) {
      capture.start(TraceCaptureConfig.defaults()); // samplingProbability = 0.0
      final var tracer = provider.get("test");

      // when - untraced root span
      tracer.spanBuilder("untraced-root").startSpan().end();

      // when - remote unsampled parent
      final var remoteUnsampled =
          SpanContext.createFromRemoteParent(
              "1af7651916cd43dd8448eb211c80319c",
              "b7ad6b7169203332",
              TraceFlags.getDefault(),
              TraceState.getDefault());
      tracer
          .spanBuilder("remote-unsampled")
          .setParent(Context.root().with(Span.wrap(remoteUnsampled)))
          .startSpan()
          .end();

      // when - remote sampled parent
      final var remoteSampled =
          SpanContext.createFromRemoteParent(
              "2af7651916cd43dd8448eb211c80319c",
              "b7ad6b7169203333",
              TraceFlags.getSampled(),
              TraceState.getDefault());
      tracer
          .spanBuilder("remote-sampled")
          .setParent(Context.root().with(Span.wrap(remoteSampled)))
          .startSpan()
          .end();

      // then
      assertThat(capture.snapshot(null).spans())
          .extracting(s -> s.getName())
          .containsExactly("remote-sampled");
    }
  }

  @Test
  void shouldRateLimitHeadSampledRootSpansAndIgnoreOutOfOrderPastNanos() {
    // given
    final var nanoTime = new AtomicLong(10_000_000_000L);
    try (final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/actuator/traces");
        final var provider = newTracerProvider(capture, Sampler.parentBased(Sampler.alwaysOff()))) {
      capture.start(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 2, 100));
      final var tracer = provider.get("test");

      // when - 4 root spans in second 0 (limit = 2)
      for (int i = 0; i < 4; i++) {
        tracer.spanBuilder("sec0-" + i).startSpan().end();
      }

      // advance to second 1, exhaust quota (2 root spans, each with a child)
      nanoTime.set(11_000_000_000L);
      for (int i = 0; i < 3; i++) {
        final var parent = tracer.spanBuilder("sec1-parent-" + i).startSpan();
        try (final var ignored = parent.makeCurrent()) {
          tracer.spanBuilder("sec1-child-" + i).startSpan().end();
        }
        parent.end();
      }

      // simulate a preempted thread arriving with an older timestamp from second 0 -> must NOT
      // rewind or reset second 1's exhausted quota
      nanoTime.set(10_999_000_000L);
      tracer.spanBuilder("stale-sec0").startSpan().end();
      nanoTime.set(11_500_000_000L);
      tracer.spanBuilder("sec1-after-stale").startSpan().end();

      // then - only 2 root spans in second 0 + 2 (parent + child) in second 1
      assertThat(capture.snapshot(null).spans())
          .extracting(s -> s.getName())
          .containsExactly(
              "sec0-0", "sec0-1", "sec1-child-0", "sec1-parent-0", "sec1-child-1", "sec1-parent-1");
    }
  }

  @Test
  void shouldAutoExpireCaptureAndPurgeBufferAfterRetentionTtl() {
    // given
    final var nanoTime = new AtomicLong(5_000_000_000L);
    final var retention = Duration.ofSeconds(30);
    try (final var capture = newCapture(nanoTime, retention, "/actuator/traces");
        final var provider = newTracerProvider(capture, Sampler.parentBased(Sampler.alwaysOff()))) {
      capture.start(new TraceCaptureConfig(Duration.ofSeconds(10), 1.0, 100, 100));
      final var tracer = provider.get("test");

      tracer.spanBuilder("before-expiry").startSpan().end();

      // when - advance past 10s capture window (within 30s retention window)
      nanoTime.addAndGet(Duration.ofSeconds(10).toNanos());
      tracer.spanBuilder("after-expiry").startSpan().end();

      // then - capture stopped, buffered spans still retained
      final var expiredStatus = capture.status();
      assertThat(expiredStatus.active()).isFalse();
      assertThat(expiredStatus.capturedSpans()).isEqualTo(1);
      assertThat(capture.snapshot(null).spans())
          .extracting(s -> s.getName())
          .containsExactly("before-expiry");

      // when - advance past 30s retention TTL
      nanoTime.addAndGet(retention.toNanos());

      // then - buffer is automatically purged
      assertThat(capture.status().capturedSpans()).isZero();
      assertThat(capture.status().startedAt()).isNull();
      assertThat(capture.snapshot(null).spans()).isEmpty();
    }
  }

  @Test
  void shouldEvictOldestSpansWhenRingBufferIsFull() {
    // given
    final var nanoTime = new AtomicLong(1_000_000_000L);
    try (final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/actuator/traces");
        final var provider = newTracerProvider(capture, Sampler.parentBased(Sampler.alwaysOff()))) {
      capture.start(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 500, 100));
      final var tracer = provider.get("test");

      // when
      for (int i = 0; i < 125; i++) {
        tracer.spanBuilder("span-" + i).startSpan().end();
      }

      // then
      final var snapshot = capture.snapshot(null);
      assertThat(snapshot.status().capturedSpans()).isEqualTo(100);
      assertThat(snapshot.status().evictedSpans()).isEqualTo(25);
      assertThat(snapshot.spans().getFirst().getName()).isEqualTo("span-25");
      assertThat(snapshot.spans().getLast().getName()).isEqualTo("span-124");
    }
  }

  @Test
  void shouldFilterByTraceIdAndExcludeConfiguredTracesActuatorPath() {
    // given - customized management base path
    final var nanoTime = new AtomicLong(1_000_000_000L);
    try (final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/management/traces");
        final var provider = newTracerProvider(capture, Sampler.parentBased(Sampler.alwaysOff()))) {
      capture.start(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 100, 100));
      final var tracer = provider.get("test");

      // when
      final var spanA = tracer.spanBuilder("request-a").startSpan();
      final var traceIdA = spanA.getSpanContext().getTraceId();
      spanA.end();

      tracer.spanBuilder("request-b").startSpan().end();
      tracer
          .spanBuilder("http get /management/traces/export")
          .setSpanKind(SpanKind.SERVER)
          .setAllAttributes(
              Attributes.of(AttributeKey.stringKey("url.path"), "/management/traces/export"))
          .startSpan()
          .end();

      // then
      assertThat(capture.snapshot(null).spans())
          .extracting(s -> s.getName())
          .containsExactly("request-a", "request-b");
      assertThat(capture.snapshot(traceIdA).spans())
          .extracting(s -> s.getName())
          .containsExactly("request-a");
    }
  }

  @Test
  void shouldPreserveDelegateSamplerAttributesForSampledChildSpans() {
    // given - custom delegate sampler that attaches an attribute when sampling
    final var nanoTime = new AtomicLong(1_000_000_000L);
    final var customKey = AttributeKey.stringKey("custom.sampler.tag");
    final Sampler customDelegate =
        new Sampler() {
          @Override
          public SamplingResult shouldSample(
              final Context parentContext,
              final String traceId,
              final String name,
              final SpanKind spanKind,
              final Attributes attributes,
              final List<LinkData> parentLinks) {
            return SamplingResult.create(
                io.opentelemetry.sdk.trace.samplers.SamplingDecision.RECORD_AND_SAMPLE,
                Attributes.of(customKey, "from-delegate"));
          }

          @Override
          public String getDescription() {
            return "custom";
          }
        };

    try (final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/actuator/traces");
        final var provider = newTracerProvider(capture, customDelegate)) {
      capture.start(TraceCaptureConfig.defaults());
      final var tracer = provider.get("test");

      // when
      final var remoteSampled =
          SpanContext.createFromRemoteParent(
              "3af7651916cd43dd8448eb211c80319c",
              "b7ad6b7169203334",
              TraceFlags.getSampled(),
              TraceState.getDefault());
      tracer
          .spanBuilder("child")
          .setParent(Context.root().with(Span.wrap(remoteSampled)))
          .startSpan()
          .end();

      // then
      final var captured = capture.snapshot(null).spans();
      assertThat(captured).hasSize(1);
      assertThat(captured.getFirst().getAttributes().get(customKey)).isEqualTo("from-delegate");
    }
  }

  @Test
  void shouldStopAndClearBufferOnClear() {
    // given
    final var nanoTime = new AtomicLong(1_000_000_000L);
    try (final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/actuator/traces");
        final var provider = newTracerProvider(capture, Sampler.parentBased(Sampler.alwaysOff()))) {
      capture.start(new TraceCaptureConfig(Duration.ofSeconds(60), 1.0, 100, 100));
      final var tracer = provider.get("test");
      tracer.spanBuilder("captured").startSpan().end();
      assertThat(capture.status().capturedSpans()).isEqualTo(1);

      // when
      final var clearedStatus = capture.clear();
      tracer.spanBuilder("after-clear").startSpan().end();

      // then
      assertThat(clearedStatus.active()).isFalse();
      assertThat(clearedStatus.capturedSpans()).isZero();
      assertThat(capture.snapshot(null).spans()).isEmpty();
    }
  }

  @Test
  void shouldRejectStartIfInactiveWhenAlreadyActiveAndNoOpAfterClose() {
    // given
    final var nanoTime = new AtomicLong(1_000_000_000L);
    final var capture = newCapture(nanoTime, Duration.ofMinutes(15), "/actuator/traces");

    // when - first startIfInactive succeeds, second returns null
    assertThat(capture.startIfInactive(TraceCaptureConfig.defaults())).isNotNull();
    assertThat(capture.startIfInactive(TraceCaptureConfig.defaults())).isNull();

    // when - close then start -> must not throw RejectedExecutionException or reactivate
    capture.close();
    final var afterClose = capture.start(TraceCaptureConfig.defaults());

    // then
    assertThat(afterClose.active()).isFalse();
    assertThat(capture.status().active()).isFalse();
  }

  private static LocalTraceCapture newCapture(
      final AtomicLong nanoTime, final Duration retention, final String path) {
    final var capture =
        new LocalTraceCapture(
            Clock.fixed(START_INSTANT, ZoneOffset.UTC), nanoTime::get, retention, path);
    capture.markSdkAttached();
    return capture;
  }

  private static SdkTracerProvider newTracerProvider(
      final LocalTraceCapture capture, final Sampler baselineSampler) {
    return SdkTracerProvider.builder()
        .setSampler(capture.createSampler(baselineSampler))
        .addSpanProcessor(capture.spanProcessor())
        .build();
  }
}
