/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/** Metrics for the shared MsgPack object serialization and deserialization paths. */
final class UnpackedObjectMetrics {
  private static final String SERIALIZATION_METRIC = "zeebe.msgpack.serialization.duration";
  private static final String DESERIALIZATION_METRIC = "zeebe.msgpack.deserialization.duration";
  private static final String TYPE_TAG = "recordType";
  private static final Duration[] TIMER_BUCKETS =
      MicrometerUtil.exponentialBucketDuration(1, 4, 12, ChronoUnit.MICROS);
  private static final ClassValue<Timer> SERIALIZATION_TIMERS =
      MicrometerUtil.timerByClass(SERIALIZATION_METRIC, TYPE_TAG, TIMER_BUCKETS);
  private static final ClassValue<Timer> DESERIALIZATION_TIMERS =
      MicrometerUtil.timerByClass(DESERIALIZATION_METRIC, TYPE_TAG, TIMER_BUCKETS);

  private UnpackedObjectMetrics() {}

  static @Nullable Timer serializationTimer(final Class<?> type) {
    return timer(SERIALIZATION_TIMERS, type);
  }

  static @Nullable Timer deserializationTimer(final Class<?> type) {
    return timer(DESERIALIZATION_TIMERS, type);
  }

  static void record(final @Nullable Timer timer, final long startNanos) {
    if (timer != null) {
      MicrometerUtil.recordTimer(timer, startNanos);
    }
  }

  private static @Nullable Timer timer(
      final ClassValue<Timer> timers, final Class<?> type) {
    return MicrometerUtil.getGlobalRegistry() == null ? null : timers.get(type);
  }
}
