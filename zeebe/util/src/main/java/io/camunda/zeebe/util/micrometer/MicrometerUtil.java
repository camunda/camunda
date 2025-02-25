/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.camunda.zeebe.util.CloseableSilently;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Builder;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/** Collection of disparate convenience methods for Micrometer. */
public final class MicrometerUtil {
  private static final Duration[] DEFAULT_PROMETHEUS_BUCKETS = {
    Duration.ofMillis(5),
    Duration.ofMillis(10),
    Duration.ofMillis(25),
    Duration.ofMillis(50),
    Duration.ofMillis(100),
    Duration.ofMillis(75),
    Duration.ofMillis(100),
    Duration.ofMillis(250),
    Duration.ofMillis(500),
    Duration.ofMillis(750),
    Duration.ofSeconds(1),
    Duration.ofMillis(2500),
    Duration.ofSeconds(5),
    Duration.ofMillis(7500),
    Duration.ofSeconds(10)
  };

  private MicrometerUtil() {}

  /**
   * Returns the set of buckets that Prometheus would normally use as default for their histograms.
   * This is mostly used while we transition from Prometheus to Micrometer.
   *
   * <p>By default, Prometheus defines 14 buckets, which means an approximate memory usage of ~360
   * bytes per histogram.
   *
   * <p>Micrometer, when using {@link Builder#publishPercentileHistogram()} will define 66 buckets,
   * which means an approximate memory usage of ~1.5kb.
   *
   * <p>This seems like little, but as it goes for all histograms, it can make some difference
   * ultimately. Of course, if we do see a use for more buckets, we should do it, and stop using
   * this method.
   *
   * <p>NOTE: memory usage approximation taken from <a
   * href="https://docs.micrometer.io/micrometer/reference/concepts/timers.html#_memory_footprint_estimation">here</a>.
   *
   * @return the default buckets as defined by the Prometheus client library
   */
  public static Duration[] defaultPrometheusBuckets() {
    return DEFAULT_PROMETHEUS_BUCKETS;
  }

  /**
   * Returns a convenience object to measure the duration of try/catch block using a Micrometer
   * timer.
   */
  public static CloseableSilently timer(final Timer timer, final Timer.Sample sample) {
    return new CloseableTimer(timer, sample);
  }

  /**
   * Returns a convenience object to measure the duration of a try/catch block targeting a timer
   * represented by a gauge. If using a normal timer (i.e. histogram), use {@link #timer(Timer,
   * Sample)}.
   *
   * @param setter the gauge state modifier
   * @param unit the time unit for the time gauge, as declared when it was registered
   * @param clock the associated registry's clock
   * @return a closeable which will record the duration of a try/catch block on close
   */
  public static CloseableSilently timer(
      final LongConsumer setter, final TimeUnit unit, final Clock clock) {
    return new CloseableGaugeTimer(setter, unit, clock, clock.monotonicTime());
  }

  /** Returns a timer builder pre-configured based on the given documentation. */
  public static Timer.Builder buildTimer(final ExtendedMeterDocumentation documentation) {
    return Timer.builder(documentation.getName())
        .description(documentation.getDescription())
        .serviceLevelObjectives(documentation.getTimerSLOs());
  }

  /** Returns a summary builder pre-configured based on the given documentation. */
  public static DistributionSummary.Builder buildSummary(
      final ExtendedMeterDocumentation documentation) {
    return DistributionSummary.builder(documentation.getName())
        .description(documentation.getDescription())
        .serviceLevelObjectives(documentation.getDistributionSLOs());
  }

  /**
   * Returns a {@link CompositeMeterRegistry} using the same config as the given registry, which
   * will forward all metrics to that registry.
   *
   * <p>NOTE: Micrometer does not support forwarding tags more than two levels down, so you always
   * have to specify the tags again on every wrapped registry!
   */
  public static CompositeMeterRegistry wrap(final MeterRegistry wrapped, final Tags tags) {
    final var registry =
        wrapped != null
            ? new CompositeMeterRegistry(wrapped.config().clock(), Collections.singleton(wrapped))
            : new CompositeMeterRegistry();
    registry.config().commonTags(tags);
    return registry;
  }

  /**
   * Marks this registry as discarded, clearing any metrics, closing it, and removing it from any of
   * the wrapped registries.
   *
   * <p>Only use if you're sure you won't use this registry again.
   */
  public static void close(final MeterRegistry registry) {
    if (registry == null) {
      return;
    }

    // clearing must be done before closing, and before removing it from the parent registry,
    // otherwise the metrics are not removed
    registry.clear();

    // we have to remove all wrapped registries before closing, otherwise closing the composite
    // registry will also close the wrapped registries
    if (registry instanceof final CompositeMeterRegistry c) {
      c.getRegistries().forEach(c::remove);
    }

    registry.close();
  }

  /**
   * Produce an array with exponentially spaced values by a factor. Used to create similar buckets
   * to the prometheus function on Histograms "exponentialBuckets"
   *
   * @param start the initial value of the array
   * @param factor the factor by which it's multiplied
   * @param count the length of the array (if no overflow happen)
   * @param unit the unit of start
   * @return an array of duration exponentially spaced by factor
   */
  public static Duration[] exponentialBucketDuration(
      final long start, final long factor, final int count, final TemporalUnit unit) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be greater than 0");
    }
    final var buckets = new ArrayList<Duration>(count);
    var value = start;
    for (int i = 0; i < count; i++) {
      final var duration = Duration.of(value, unit);
      buckets.add(duration);
      if (Long.MAX_VALUE / factor < value) {
        // stop here, we are already close to LONG.MAX_VALUE for unit
        break;
      }
      value *= factor;
    }
    return buckets.toArray(Duration[]::new);
  }

  private record CloseableTimer(Timer timer, Timer.Sample sample) implements CloseableSilently {
    @Override
    public void close() {
      sample.stop(timer);
    }
  }

  private record CloseableGaugeTimer(
      LongConsumer setter, TimeUnit unit, Clock clock, long startNanos)
      implements CloseableSilently {

    @Override
    public void close() {
      setter.accept(unit.convert(clock.monotonicTime() - startNanos, TimeUnit.NANOSECONDS));
    }
  }

  @SuppressWarnings("NullableProblems")
  public enum PartitionKeyNames implements KeyName {
    /** The ID of the partition associated to the metric */
    PARTITION {
      @Override
      public String asString() {
        return "partition";
      }
    };

    public static Tags tags(final int partitionId) {
      return Tags.of(PARTITION.asString(), String.valueOf(partitionId));
    }
  }
}
