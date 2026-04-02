/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.impl.log.SequencerMetrics.SequencerMetricsDoc.BATCH_LENGTH_BYTES;
import static io.camunda.zeebe.logstreams.impl.log.SequencerMetrics.SequencerMetricsDoc.BATCH_SIZE;
import static io.camunda.zeebe.logstreams.impl.log.SequencerMetrics.SequencerMetricsDoc.LOCK_HOLD_TIME;
import static io.camunda.zeebe.logstreams.impl.log.SequencerMetrics.SequencerMetricsDoc.LOCK_WAIT_TIME;

import io.camunda.zeebe.logstreams.impl.LogStreamMetricsDoc.FlowControlContext;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.InterPartition;
import io.camunda.zeebe.logstreams.log.WriteContext.Internal;
import io.camunda.zeebe.logstreams.log.WriteContext.ProcessingResult;
import io.camunda.zeebe.logstreams.log.WriteContext.Scheduled;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class SequencerMetrics {
  private final MeterRegistry meterRegistry;
  private final DistributionSummary batchSize;
  private final DistributionSummary batchLengthBytes;
  private final ConcurrentHashMap<FlowControlContext, Timer> lockWaitTimers =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<FlowControlContext, Timer> lockHoldTimers =
      new ConcurrentHashMap<>();

  SequencerMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    batchSize =
        DistributionSummary.builder(BATCH_SIZE.getName())
            .description(BATCH_SIZE.getDescription())
            .serviceLevelObjectives(BATCH_SIZE.getDistributionSLOs())
            .register(meterRegistry);
    batchLengthBytes =
        DistributionSummary.builder(BATCH_LENGTH_BYTES.getName())
            .description(BATCH_LENGTH_BYTES.getDescription())
            .serviceLevelObjectives(BATCH_LENGTH_BYTES.getDistributionSLOs())
            .register(meterRegistry);
  }

  void observeBatchSize(final int size) {
    batchSize.record(size);
  }

  void observeBatchLengthBytes(final int lengthBytes) {
    final int batchLengthKiloBytes = Math.floorDiv(lengthBytes, 1024);
    batchLengthBytes.record(batchLengthKiloBytes);
  }

  void observeLockWaitTime(final WriteContext waiter, final long durationNanos) {
    final var waiterTag = tagForContext(waiter);
    final var timer =
        lockWaitTimers.computeIfAbsent(
            waiterTag,
            tag ->
                Timer.builder(LOCK_WAIT_TIME.getName())
                    .description(LOCK_WAIT_TIME.getDescription())
                    .publishPercentiles(0.5, 0.9, 0.99, 0.999)
                    .tag(LockKeyNames.WAITER.asString(), tag.getValue())
                    .register(meterRegistry));
    timer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  void observeLockHoldTime(final WriteContext writer, final long durationNanos) {
    final var writerTag = tagForContext(writer);
    final var timer =
        lockHoldTimers.computeIfAbsent(
            writerTag,
            tag ->
                Timer.builder(LOCK_HOLD_TIME.getName())
                    .description(LOCK_HOLD_TIME.getDescription())
                    .publishPercentiles(0.5, 0.9, 0.99, 0.999)
                    .tag(LockKeyNames.WRITER.asString(), tag.getValue())
                    .register(meterRegistry));
    timer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  static FlowControlContext tagForContext(final WriteContext context) {
    return switch (context) {
      case final UserCommand ignored -> FlowControlContext.USER_COMMAND;
      case final ProcessingResult ignored -> FlowControlContext.PROCESSING_RESULT;
      case final InterPartition ignored -> FlowControlContext.INTER_PARTITION;
      case final Scheduled ignored -> FlowControlContext.SCHEDULED;
      case final Internal ignored -> FlowControlContext.INTERNAL;
    };
  }

  @SuppressWarnings("NullableProblems")
  public enum SequencerMetricsDoc implements ExtendedMeterDocumentation {
    /** Histogram over the number of entries in each batch that is appended */
    BATCH_SIZE {
      private static final double[] BUCKETS = {1, 2, 3, 5, 10, 25, 50, 100, 500, 1000};

      @Override
      public String getDescription() {
        return "Histogram over the number of entries in each batch that is appended";
      }

      @Override
      public String getName() {
        return "zeebe.sequencer.batch.size";
      }

      @Override
      public Type getType() {
        return Type.DISTRIBUTION_SUMMARY;
      }

      @Override
      public double[] getDistributionSLOs() {
        return BUCKETS;
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    },

    /** Histogram over the size, in Kilobytes, of the sequenced batches */
    BATCH_LENGTH_BYTES {
      private static final double[] BUCKETS = {0.256, 0.512, 1, 4, 8, 32, 128, 512, 1024, 4096};

      @Override
      public String getDescription() {
        return "Histogram over the size, in Kilobytes, of the sequenced batches";
      }

      @Override
      public String getName() {
        return "zeebe.sequencer.batch.length.bytes";
      }

      @Override
      public String getBaseUnit() {
        return "KiB";
      }

      @Override
      public Type getType() {
        return Type.DISTRIBUTION_SUMMARY;
      }

      @Override
      public double[] getDistributionSLOs() {
        return BUCKETS;
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    },

    /** Time spent waiting to acquire the sequencer lock, labeled by who is waiting */
    LOCK_WAIT_TIME {
      @Override
      public String getDescription() {
        return "Time spent waiting to acquire the sequencer lock, labeled by who is waiting";
      }

      @Override
      public String getName() {
        return "zeebe.sequencer.lock.wait.time";
      }

      @Override
      public Type getType() {
        return Type.TIMER;
      }

      @Override
      public KeyName[] getKeyNames() {
        return new KeyName[] {LockKeyNames.WAITER};
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    },

    /** Time the sequencer lock is held during a write, labeled by who held the lock */
    LOCK_HOLD_TIME {
      @Override
      public String getDescription() {
        return "Time the sequencer lock is held during a write, labeled by who held the lock";
      }

      @Override
      public String getName() {
        return "zeebe.sequencer.lock.hold.time";
      }

      @Override
      public Type getType() {
        return Type.TIMER;
      }

      @Override
      public KeyName[] getKeyNames() {
        return new KeyName[] {LockKeyNames.WRITER};
      }

      @Override
      public KeyName[] getAdditionalKeyNames() {
        return PartitionKeyNames.values();
      }
    }
  }

  enum LockKeyNames implements KeyName {
    WRITER {
      @Override
      public String asString() {
        return "writer";
      }
    },
    WAITER {
      @Override
      public String asString() {
        return "waiter";
      }
    }
  }
}
