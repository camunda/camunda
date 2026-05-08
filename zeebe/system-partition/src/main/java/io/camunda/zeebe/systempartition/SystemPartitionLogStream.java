/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;

/**
 * Helper that builds a {@link LogStream} on top of the system partition's {@link RaftPartition}.
 *
 * <p>The construction mirrors the data-partition path in {@code
 * io.camunda.zeebe.broker.system.partitions.impl.steps.LogStreamPartitionTransitionStep}, but
 * without the data-partition-specific wiring (flow control rate limits, per-partition meter
 * registry, etc.).
 *
 * <p>The {@link LogStorage} backing the log stream is constructed by the broker bootstrap (see
 * Phase 3) using {@code AtomixLogStorage} from {@code zeebe-broker} — that class transitively
 * depends on the broker module, so it cannot live here. Pass it in.
 */
public final class SystemPartitionLogStream {

  private SystemPartitionLogStream() {}

  /**
   * Build an unstarted {@link LogStream} for the system partition.
   *
   * @param raftPartition the system-partition Raft instance (used only for the partition id and log
   *     name)
   * @param logStorage the {@link LogStorage} backing the log stream — typically an instance of
   *     {@code AtomixLogStorage} created by the broker bootstrap.
   * @param maxFragmentSize the maximum log entry fragment size (typically {@code
   *     BrokerCfg.network.maxMessageSizeInBytes})
   * @param meterRegistry the meter registry used for log-stream metrics
   * @return a freshly built {@link LogStream}
   */
  public static LogStream build(
      final RaftPartition raftPartition,
      final LogStorage logStorage,
      final int maxFragmentSize,
      final MeterRegistry meterRegistry) {
    return LogStream.builder()
        .withPartitionId(raftPartition.id().id())
        .withMaxFragmentSize(maxFragmentSize)
        .withLogStorage(logStorage)
        .withLogName("system-" + raftPartition.id().id())
        .withMeterRegistry(meterRegistry)
        .withClock(InstantSource.system())
        .build();
  }
}
