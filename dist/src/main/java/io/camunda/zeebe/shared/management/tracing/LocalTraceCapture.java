/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Node-local bounded trace flight recorder.
 *
 * <p>When no capture window is active, {@link #spanProcessor()} performs a single volatile read and
 * allocates nothing, and {@link #createSampler(Sampler)} delegates directly to Spring's configured
 * sampler. While a window is active, root spans may be head-sampled up to the configured
 * probability and per-second rate limit, sampled parents are continued so distributed traces stay
 * complete, and finished sampled spans are retained in a bounded ring buffer.
 *
 * <p>Buffered spans automatically expire and are freed from heap after {@link
 * TraceCaptureConfig#DEFAULT_RETENTION_AFTER_EXPIRY} past capture expiry so forgotten captures
 * cannot permanently pin Old Gen memory.
 */
@NullMarked
public final class LocalTraceCapture implements AutoCloseable {

  private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
  private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
  private static final String DEFAULT_TRACES_ACTUATOR_PATH = "/actuator/traces";

  private final Clock wallClock;
  private final LongSupplier nanoClock;
  private final Duration retentionAfterExpiry;
  private final String tracesActuatorPath;
  private final ScheduledExecutorService cleanupScheduler;

  private final AtomicBoolean sdkAttached = new AtomicBoolean(false);
  private final Object bufferLock = new Object();
  private boolean closed;
  private ArrayDeque<SpanData> buffer = new ArrayDeque<>();
  private long evictedSpans;
  private @Nullable ScheduledFuture<?> pendingCleanup;

  private volatile @Nullable CaptureWindow window;

  public LocalTraceCapture(final String tracesActuatorPath) {
    this(
        Clock.systemUTC(),
        System::nanoTime,
        TraceCaptureConfig.DEFAULT_RETENTION_AFTER_EXPIRY,
        tracesActuatorPath);
  }

  public LocalTraceCapture() {
    this(DEFAULT_TRACES_ACTUATOR_PATH);
  }

  LocalTraceCapture(
      final Clock wallClock,
      final LongSupplier nanoClock,
      final Duration retentionAfterExpiry,
      final String tracesActuatorPath) {
    this.wallClock = Objects.requireNonNull(wallClock, "wallClock");
    this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    this.retentionAfterExpiry =
        Objects.requireNonNull(retentionAfterExpiry, "retentionAfterExpiry");
    this.tracesActuatorPath = Objects.requireNonNull(tracesActuatorPath, "tracesActuatorPath");
    cleanupScheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "trace-capture-retention");
              thread.setDaemon(true);
              return thread;
            });
  }

  /** Marks that this instance has been attached to an active {@code SdkTracerProvider}. */
  public void markSdkAttached() {
    sdkAttached.set(true);
  }

  public boolean isSdkAttached() {
    return sdkAttached.get();
  }

  /**
   * Starts a fresh capture window, discarding any expired buffer from a previous window. Expiry is
   * tracked via monotonic nanoseconds so wall-clock skew cannot prematurely end a capture.
   */
  public LocalCaptureStatus start(final TraceCaptureConfig config) {
    return Objects.requireNonNull(startInternal(config, false));
  }

  /**
   * Atomically starts a fresh capture window only if no window is currently active. Returns {@code
   * null} when a capture is already running, preventing TOCTOU races between concurrent callers.
   */
  public @Nullable LocalCaptureStatus startIfInactive(final TraceCaptureConfig config) {
    return startInternal(config, true);
  }

  private @Nullable LocalCaptureStatus startInternal(
      final TraceCaptureConfig config, final boolean requireInactive) {
    Objects.requireNonNull(config, "config");
    final long nowNanos = nanoClock.getAsLong();
    final Instant now = wallClock.instant();
    final Instant expiresAt = now.plus(config.duration());
    final Instant retentionExpiresAt = expiresAt.plus(retentionAfterExpiry);
    final long totalRetentionNanos = config.duration().plus(retentionAfterExpiry).toNanos();
    final long expiresAtNanos = nowNanos + config.duration().toNanos();
    final long retentionExpiresAtNanos = nowNanos + totalRetentionNanos;
    final var newWindow =
        new CaptureWindow(
            now,
            expiresAt,
            retentionExpiresAt,
            expiresAtNanos,
            retentionExpiresAtNanos,
            config.samplingProbability(),
            config.maxTracesPerSecond(),
            config.maxSpansPerNode(),
            config.samplingProbability() > 0.0
                ? Sampler.traceIdRatioBased(config.samplingProbability())
                : Sampler.alwaysOff(),
            new PerSecondRateLimiter(config.maxTracesPerSecond(), nowNanos));

    synchronized (bufferLock) {
      if (closed) {
        return buildStatusLocked(nowNanos);
      }
      if (requireInactive && window != null && window.isActive(nowNanos)) {
        return null;
      }
      cancelPendingCleanupLocked();
      // Replace the deque so any large previously grown backing array is released to GC.
      buffer = new ArrayDeque<>();
      evictedSpans = 0;
      window = newWindow;
      scheduleCleanupLocked(newWindow, totalRetentionNanos);
      return buildStatusLocked(nowNanos);
    }
  }

  /** Stops any active capture immediately and frees all buffered spans. */
  public LocalCaptureStatus clear() {
    final long nowNanos = nanoClock.getAsLong();
    synchronized (bufferLock) {
      cancelPendingCleanupLocked();
      buffer = new ArrayDeque<>();
      evictedSpans = 0;
      window = null;
      return buildStatusLocked(nowNanos);
    }
  }

  public LocalCaptureStatus status() {
    final long nowNanos = nanoClock.getAsLong();
    synchronized (bufferLock) {
      purgeIfRetentionExpiredLocked(nowNanos);
      return buildStatusLocked(nowNanos);
    }
  }

  /**
   * Returns a point-in-time snapshot of buffered spans, optionally filtered to one {@code traceId}.
   */
  public LocalTraceSnapshot snapshot(final @Nullable String traceId) {
    final long nowNanos = nanoClock.getAsLong();
    synchronized (bufferLock) {
      purgeIfRetentionExpiredLocked(nowNanos);
      final List<SpanData> result;
      if (traceId == null) {
        result = List.copyOf(buffer);
      } else {
        final var filtered = new ArrayList<SpanData>();
        for (final var span : buffer) {
          if (traceId.equals(span.getTraceId())) {
            filtered.add(span);
          }
        }
        result = List.copyOf(filtered);
      }
      return new LocalTraceSnapshot(buildStatusLocked(nowNanos), result);
    }
  }

  public SpanProcessor spanProcessor() {
    return new CaptureSpanProcessor();
  }

  public Sampler createSampler(final Sampler delegate) {
    Objects.requireNonNull(delegate, "delegate");
    return new DynamicCaptureSampler(delegate);
  }

  @Override
  public void close() {
    synchronized (bufferLock) {
      closed = true;
      cancelPendingCleanupLocked();
      buffer = new ArrayDeque<>();
      evictedSpans = 0;
      window = null;
      cleanupScheduler.shutdownNow();
    }
  }

  private void scheduleCleanupLocked(final CaptureWindow expectedWindow, final long delayNanos) {
    if (!closed && !cleanupScheduler.isShutdown()) {
      pendingCleanup =
          cleanupScheduler.schedule(
              () -> purgeIfRetentionExpired(expectedWindow),
              Math.max(1L, delayNanos),
              TimeUnit.NANOSECONDS);
    }
  }

  private void purgeIfRetentionExpired(final CaptureWindow expectedWindow) {
    synchronized (bufferLock) {
      if (closed || window != expectedWindow) {
        return;
      }
      final long nowNanos = nanoClock.getAsLong();
      if (expectedWindow.isRetentionExpired(nowNanos)) {
        buffer = new ArrayDeque<>();
        evictedSpans = 0;
        window = null;
        pendingCleanup = null;
      } else {
        // Reschedule for any remaining nanoseconds (for example under timer jitter).
        scheduleCleanupLocked(expectedWindow, expectedWindow.retentionExpiresAtNanos() - nowNanos);
      }
    }
  }

  private void purgeIfRetentionExpiredLocked(final long nowNanos) {
    final var current = window;
    if (current != null && current.isRetentionExpired(nowNanos)) {
      cancelPendingCleanupLocked();
      buffer = new ArrayDeque<>();
      evictedSpans = 0;
      window = null;
    }
  }

  private void cancelPendingCleanupLocked() {
    if (pendingCleanup != null) {
      pendingCleanup.cancel(false);
      pendingCleanup = null;
    }
  }

  private LocalCaptureStatus buildStatusLocked(final long nowNanos) {
    final var current = window;
    final boolean enabled = sdkAttached.get();
    if (current == null) {
      return new LocalCaptureStatus(
          false, enabled, null, null, null, 0.0, 0, 0, buffer.size(), evictedSpans);
    }
    return new LocalCaptureStatus(
        current.isActive(nowNanos),
        enabled,
        current.startedAt(),
        current.expiresAt(),
        current.retentionExpiresAt(),
        current.samplingProbability(),
        current.maxTracesPerSecond(),
        current.maxSpans(),
        buffer.size(),
        evictedSpans);
  }

  private boolean isTraceEndpointSpan(final SpanData span) {
    return isTraceEndpointPath(span.getAttributes().get(URL_PATH))
        || isTraceEndpointPath(span.getAttributes().get(HTTP_ROUTE))
        || span.getName().contains(tracesActuatorPath);
  }

  private boolean isTraceEndpointStart(final String name, final Attributes attributes) {
    return isTraceEndpointPath(attributes.get(URL_PATH))
        || isTraceEndpointPath(attributes.get(HTTP_ROUTE))
        || name.contains(tracesActuatorPath);
  }

  private boolean isTraceEndpointPath(final @Nullable String path) {
    return path != null && path.contains(tracesActuatorPath);
  }

  private final class CaptureSpanProcessor implements SpanProcessor {

    @Override
    public void onStart(final Context parentContext, final ReadWriteSpan span) {
      // Nothing to do on start.
    }

    @Override
    public boolean isStartRequired() {
      return false;
    }

    @Override
    public void onEnd(final ReadableSpan span) {
      final var current = window;
      if (current == null || !current.isActive(nanoClock.getAsLong())) {
        return;
      }
      if (!span.getSpanContext().isSampled()) {
        return;
      }
      final var data = span.toSpanData();
      if (isTraceEndpointSpan(data)) {
        return;
      }
      synchronized (bufferLock) {
        if (window != current) {
          return;
        }
        while (buffer.size() >= current.maxSpans()) {
          buffer.removeFirst();
          evictedSpans++;
        }
        buffer.addLast(data);
      }
    }

    @Override
    public boolean isEndRequired() {
      return true;
    }
  }

  private final class DynamicCaptureSampler implements Sampler {

    private final Sampler delegate;

    private DynamicCaptureSampler(final Sampler delegate) {
      this.delegate = delegate;
    }

    @Override
    public SamplingResult shouldSample(
        final Context parentContext,
        final String traceId,
        final String name,
        final SpanKind spanKind,
        final Attributes attributes,
        final List<LinkData> parentLinks) {
      final var current = window;
      if (current == null) {
        return delegate.shouldSample(
            parentContext, traceId, name, spanKind, attributes, parentLinks);
      }
      final long nowNanos = nanoClock.getAsLong();
      if (!current.isActive(nowNanos)) {
        return delegate.shouldSample(
            parentContext, traceId, name, spanKind, attributes, parentLinks);
      }

      if (isTraceEndpointStart(name, attributes)) {
        return delegate.shouldSample(
            parentContext, traceId, name, spanKind, attributes, parentLinks);
      }

      final var delegateResult =
          delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
      final var parentSpanContext = Span.fromContext(parentContext).getSpanContext();
      if (parentSpanContext.isValid()) {
        if (!parentSpanContext.isSampled()) {
          return delegateResult;
        }
        // Preserve any attributes or TraceState added by the delegate when it already samples.
        return delegateResult.getDecision() == SamplingDecision.RECORD_AND_SAMPLE
            ? delegateResult
            : SamplingResult.recordAndSample();
      }

      if (delegateResult.getDecision() == SamplingDecision.RECORD_AND_SAMPLE) {
        return delegateResult;
      }

      if (current.samplingProbability() <= 0.0) {
        return delegateResult;
      }

      // Roll probability before consuming a rate-limiter token so unsampled requests don't burn
      // budget.
      final var ratioDecision =
          current
              .rootRatioSampler()
              .shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
      if (ratioDecision.getDecision() != SamplingDecision.RECORD_AND_SAMPLE) {
        return SamplingResult.drop();
      }

      return current.rateLimiter().tryAcquire(nowNanos)
          ? SamplingResult.recordAndSample()
          : SamplingResult.drop();
    }

    @Override
    public String getDescription() {
      return "DynamicCaptureSampler{" + delegate.getDescription() + "}";
    }
  }

  private record CaptureWindow(
      Instant startedAt,
      Instant expiresAt,
      Instant retentionExpiresAt,
      long expiresAtNanos,
      long retentionExpiresAtNanos,
      double samplingProbability,
      int maxTracesPerSecond,
      int maxSpans,
      Sampler rootRatioSampler,
      PerSecondRateLimiter rateLimiter) {

    boolean isActive(final long nowNanos) {
      return nowNanos - expiresAtNanos < 0;
    }

    boolean isRetentionExpired(final long nowNanos) {
      return nowNanos - retentionExpiresAtNanos >= 0;
    }
  }

  /**
   * Lock-free monotonic 1-second window limiter packing {@code (elapsedSecond << 32) | count}.
   * Out-of-order calls with an earlier second never rewind the window.
   */
  private static final class PerSecondRateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int maxPerSecond;
    private final long originNanos;
    private final AtomicLong state = new AtomicLong(-1L);

    private PerSecondRateLimiter(final int maxPerSecond, final long originNanos) {
      this.maxPerSecond = maxPerSecond;
      this.originNanos = originNanos;
    }

    boolean tryAcquire(final long nowNanos) {
      final long elapsedSeconds = Math.max(0L, (nowNanos - originNanos) / NANOS_PER_SECOND);
      while (true) {
        final long current = state.get();
        final long currentSecond = current >>> 32;
        final int count = (int) current;
        final long next;
        if (current == -1L || elapsedSeconds > currentSecond) {
          next = (elapsedSeconds << 32) | 1L;
        } else if (elapsedSeconds < currentSecond) {
          // A preempted thread arrived from a past second; never rewind the window.
          return false;
        } else if (count < maxPerSecond) {
          next = (elapsedSeconds << 32) | ((count + 1L) & 0xFFFF_FFFFL);
        } else {
          return false;
        }
        if (state.compareAndSet(current, next)) {
          return true;
        }
      }
    }
  }

  public record LocalCaptureStatus(
      boolean active,
      boolean sdkEnabled,
      @Nullable Instant startedAt,
      @Nullable Instant expiresAt,
      @Nullable Instant retentionExpiresAt,
      double samplingProbability,
      int maxTracesPerSecond,
      int maxSpans,
      int capturedSpans,
      long evictedSpans) {}

  public record LocalTraceSnapshot(LocalCaptureStatus status, List<SpanData> spans) {}
}
