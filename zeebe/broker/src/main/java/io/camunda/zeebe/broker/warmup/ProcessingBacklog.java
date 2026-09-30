/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

/**
 * The largest processing backlog among the broker's partitions, in log positions. Every record a
 * leader writes while processing a command follows the records appended before it was processed, so
 * the distance from the last replayed record back to the command it came from is how far the
 * leader's processing lagged behind its log. A follower sees this without asking the leader.
 */
@NullMarked
final class ProcessingBacklog implements LongSupplier {

  private static final long TIMEOUT_MILLIS = 500;

  private final Supplier<Collection<ZeebePartition>> partitions;

  ProcessingBacklog(final Supplier<Collection<ZeebePartition>> partitions) {
    this.partitions = partitions;
  }

  /** Returns the largest backlog, or a negative value if it is unknown on every partition. */
  @Override
  public long getAsLong() {
    long largest = -1;
    for (final var partition : partitions.get()) {
      try {
        final var streamProcessor =
            partition.getStreamProcessor().join(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (streamProcessor.isPresent()) {
          largest = Math.max(largest, backlog(streamProcessor.get()));
        }
      } catch (final RuntimeException e) {
        // the partition is transitioning or its actor is busy; it counts as unknown
      }
    }
    return largest;
  }

  private static long backlog(final StreamProcessor streamProcessor) {
    final long written =
        streamProcessor.getLastWrittenPositionAsync().join(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    final long processed =
        streamProcessor.getLastProcessedPositionAsync().join(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    return written >= 0 && processed >= 0 ? Math.max(0, written - processed) : -1;
  }
}
