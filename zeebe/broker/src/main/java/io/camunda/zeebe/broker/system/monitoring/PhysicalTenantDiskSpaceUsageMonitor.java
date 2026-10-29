/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.file.SegmentAllocator;
import io.camunda.zeebe.scheduler.Actor;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.util.unit.DataSize;

/**
 * The disk space usage monitor seen by the partitions of one physical tenant. It reports the disk
 * space as not available when the volume is out of free space, as the broker-wide monitor does, or
 * when the data of this tenant uses more than its processing limit. Other tenants on the broker are
 * not affected by the second condition.
 *
 * <p>It also bounds the replication of the tenant: a journal segment allocator decorated by {@link
 * #limitSegmentAllocation(SegmentAllocator)} refuses to allocate a segment that would take the
 * tenant above its replication limit, which pauses appends on followers the same way a full volume
 * does.
 */
@NullMarked
public final class PhysicalTenantDiskSpaceUsageMonitor extends Actor
    implements DiskSpaceUsageMonitor, DiskSpaceUsageListener {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final long UNLIMITED = Long.MAX_VALUE;

  private final String physicalTenantId;
  private final DiskSpaceUsageMonitor volumeMonitor;
  private final LongSupplier usedSpaceSupplier;
  private final Duration monitoringInterval;
  private final long processingLimit;
  private final long replicationLimit;
  private final MeterRegistry meterRegistry;
  private final Set<DiskSpaceUsageListener> listeners = new HashSet<>();
  private final AtomicLong usedBytes = new AtomicLong();
  private @Nullable PhysicalTenantDiskUsageMetrics metrics;
  private boolean volumeAvailable = true;
  private boolean belowProcessingLimit = true;
  private boolean available = true;

  PhysicalTenantDiskSpaceUsageMonitor(
      final String physicalTenantId,
      final DiskSpaceUsageMonitor volumeMonitor,
      final LongSupplier usedSpaceSupplier,
      final Duration monitoringInterval,
      final long processingLimit,
      final long replicationLimit,
      final MeterRegistry meterRegistry) {
    super(
        "DiskSpaceUsageMonitor-" + physicalTenantId,
        null,
        Map.of(ACTOR_PROP_PHYSICAL_TENANT, physicalTenantId));
    this.physicalTenantId = physicalTenantId;
    this.volumeMonitor = volumeMonitor;
    this.usedSpaceSupplier = usedSpaceSupplier;
    this.monitoringInterval = monitoringInterval;
    this.processingLimit = processingLimit;
    this.replicationLimit = replicationLimit;
    this.meterRegistry = meterRegistry;
  }

  public static PhysicalTenantDiskSpaceUsageMonitor create(
      final String physicalTenantId,
      final DiskSpaceUsageMonitor volumeMonitor,
      final DataCfg dataCfg,
      final MeterRegistry meterRegistry) {
    final var roots = new ArrayList<Path>();
    roots.add(Path.of(dataCfg.getDirectory()).resolve(physicalTenantId));
    if (dataCfg.useSeparateRuntimeDirectory()) {
      roots.add(Path.of(dataCfg.getRuntimeDirectory()).resolve(physicalTenantId));
    }
    final var usedSpace = dataCfg.getDisk().getUsedSpace();
    return new PhysicalTenantDiskSpaceUsageMonitor(
        physicalTenantId,
        volumeMonitor,
        new DiskUsageScanner(roots),
        dataCfg.getDisk().getMonitoringInterval(),
        toBytes(usedSpace.getProcessing()),
        toBytes(usedSpace.getReplication()),
        meterRegistry);
  }

  @Override
  protected void onActorStarting() {
    final var startedMetrics =
        new PhysicalTenantDiskUsageMetrics(meterRegistry, physicalTenantId, usedBytes::get);
    if (processingLimit != UNLIMITED) {
      startedMetrics.registerLimit("processing", processingLimit);
    }
    if (replicationLimit != UNLIMITED) {
      startedMetrics.registerLimit("replication", replicationLimit);
    }
    metrics = startedMetrics;
  }

  @Override
  protected void onActorStarted() {
    volumeMonitor.addDiskUsageListener(this);
    measureUsedSpace();
    actor.runAtFixedRate(monitoringInterval, this::measureUsedSpace);
  }

  @Override
  protected void onActorClosing() {
    volumeMonitor.removeDiskUsageListener(this);
    if (metrics != null) {
      metrics.close();
    }
  }

  @Override
  public void addDiskUsageListener(final DiskSpaceUsageListener listener) {
    actor.call(
        () -> {
          listeners.add(listener);
          if (!available) {
            listener.onDiskSpaceNotAvailable();
          }
        });
  }

  @Override
  public void removeDiskUsageListener(final DiskSpaceUsageListener listener) {
    actor.call(() -> listeners.remove(listener));
  }

  @Override
  public void setFreeDiskSpaceSupplier(final LongSupplier freeDiskSpaceSupplier) {
    volumeMonitor.setFreeDiskSpaceSupplier(freeDiskSpaceSupplier);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(
        () -> {
          volumeAvailable = false;
          notifyOnChange();
        });
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(
        () -> {
          volumeAvailable = true;
          notifyOnChange();
        });
  }

  /**
   * Decorates the given allocator so that the journal of a partition of this tenant cannot allocate
   * a segment that takes the tenant above its replication limit. The journal reports the refusal as
   * {@link JournalException.OutOfDiskSpace}, which a follower answers by rejecting the append and
   * compacting its log.
   */
  public SegmentAllocator limitSegmentAllocation(final SegmentAllocator allocator) {
    return new SegmentAllocator() {
      @Override
      public void checkCanAllocate(final long segmentSize) {
        allocator.checkCanAllocate(segmentSize);
        if (!tryReserve(segmentSize)) {
          throw new JournalException.OutOfDiskSpace(
              "Physical tenant '%s' uses %d bytes; a new segment of %d bytes would exceed its replication limit of %d bytes"
                  .formatted(physicalTenantId, usedBytes.get(), segmentSize, replicationLimit));
        }
      }

      @Override
      public void allocate(
          final FileChannel channel, final FileDescriptor fileDescriptor, final long segmentSize)
          throws IOException {
        allocator.allocate(channel, fileDescriptor, segmentSize);
      }
    };
  }

  private boolean tryReserve(final long bytes) {
    long current;
    do {
      current = usedBytes.get();
      if (current + bytes > replicationLimit) {
        return false;
      }
    } while (!usedBytes.compareAndSet(current, current + bytes));
    return true;
  }

  private void measureUsedSpace() {
    final long used;
    try {
      used =
          Objects.requireNonNull(
              Objects.requireNonNull(metrics).scanDuration().record(usedSpaceSupplier::getAsLong));
    } catch (final UncheckedIOException e) {
      LOG.warn(
          "Failed to measure the disk space used by physical tenant '{}', keeping the last measurement",
          physicalTenantId,
          e);
      return;
    }
    usedBytes.set(used);

    final boolean wasBelowProcessingLimit = belowProcessingLimit;
    belowProcessingLimit = used <= processingLimit;
    if (wasBelowProcessingLimit && !belowProcessingLimit) {
      LOG.warn(
          "Physical tenant '{}' uses {} bytes of disk space, above its processing limit of {} bytes. Rejecting its commands until its usage drops.",
          physicalTenantId,
          used,
          processingLimit);
    } else if (!wasBelowProcessingLimit && belowProcessingLimit) {
      LOG.info(
          "Physical tenant '{}' uses {} bytes of disk space, below its processing limit of {} bytes again.",
          physicalTenantId,
          used,
          processingLimit);
    }
    notifyOnChange();
  }

  private void notifyOnChange() {
    final boolean wasAvailable = available;
    available = volumeAvailable && belowProcessingLimit;
    if (wasAvailable && !available) {
      listeners.forEach(DiskSpaceUsageListener::onDiskSpaceNotAvailable);
    } else if (!wasAvailable && available) {
      listeners.forEach(DiskSpaceUsageListener::onDiskSpaceAvailable);
    }
  }

  private static long toBytes(final @Nullable DataSize limit) {
    return limit == null ? UNLIMITED : limit.toBytes();
  }
}
