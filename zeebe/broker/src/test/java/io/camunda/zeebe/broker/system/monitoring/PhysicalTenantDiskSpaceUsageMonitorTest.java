/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.journal.JournalException.OutOfDiskSpace;
import io.camunda.zeebe.journal.file.SegmentAllocator;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class PhysicalTenantDiskSpaceUsageMonitorTest {

  private static final Duration INTERVAL = Duration.ofSeconds(1);
  private static final long PROCESSING_LIMIT = 1_000;
  private static final long REPLICATION_LIMIT = 1_500;
  private static final String TENANT = "tenanta";

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  private final AtomicLong usedSpace = new AtomicLong();
  private final StubVolumeMonitor volumeMonitor = new StubVolumeMonitor();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RecordingListener listener = new RecordingListener();
  private PhysicalTenantDiskSpaceUsageMonitor monitor;

  @BeforeEach
  void setUp() {
    monitor =
        new PhysicalTenantDiskSpaceUsageMonitor(
            TENANT,
            volumeMonitor,
            usedSpace::get,
            INTERVAL,
            PROCESSING_LIMIT,
            REPLICATION_LIMIT,
            meterRegistry);
    scheduler.submitActor(monitor);
    scheduler.workUntilDone();
    monitor.addDiskUsageListener(listener);
    scheduler.workUntilDone();
  }

  @AfterEach
  void tearDown() {
    monitor.closeAsync();
    scheduler.workUntilDone();
    meterRegistry.close();
  }

  @Test
  void shouldReportDiskSpaceNotAvailableWhenTenantUsageExceedsProcessingLimit() {
    // given
    usedSpace.set(PROCESSING_LIMIT + 1);

    // when
    measure();

    // then
    assertThat(listener.available).isFalse();
  }

  @Test
  void shouldReportDiskSpaceAvailableAgainWhenTenantUsageDrops() {
    // given
    usedSpace.set(PROCESSING_LIMIT + 1);
    measure();

    // when
    usedSpace.set(PROCESSING_LIMIT);
    measure();

    // then
    assertThat(listener.available).isTrue();
  }

  @Test
  void shouldReportDiskSpaceNotAvailableWhenVolumeIsFull() {
    // given
    usedSpace.set(0);

    // when
    volumeMonitor.notifyNotAvailable();
    scheduler.workUntilDone();

    // then
    assertThat(listener.available).isFalse();
  }

  @Test
  void shouldStayNotAvailableUntilVolumeAndTenantRecover() {
    // given
    usedSpace.set(PROCESSING_LIMIT + 1);
    measure();
    volumeMonitor.notifyNotAvailable();
    scheduler.workUntilDone();

    // when
    usedSpace.set(0);
    measure();

    // then
    assertThat(listener.available).isFalse();

    // when
    volumeMonitor.notifyAvailable();
    scheduler.workUntilDone();

    // then
    assertThat(listener.available).isTrue();
  }

  @Test
  void shouldNotifyListenerAddedWhileOverTheLimit() {
    // given
    usedSpace.set(PROCESSING_LIMIT + 1);
    measure();
    final var lateListener = new RecordingListener();

    // when
    monitor.addDiskUsageListener(lateListener);
    scheduler.workUntilDone();

    // then
    assertThat(lateListener.available).isFalse();
  }

  @Test
  void shouldRefuseSegmentAllocationAboveReplicationLimit() {
    // given
    usedSpace.set(REPLICATION_LIMIT - 100);
    measure();
    final var allocator = monitor.limitSegmentAllocation(SegmentAllocator.noop());

    // when - then
    assertThatThrownBy(() -> allocator.checkCanAllocate(101))
        .isInstanceOf(OutOfDiskSpace.class)
        .hasMessageContaining(TENANT)
        .hasMessageContaining("replication limit");
  }

  @Test
  void shouldCountAllocatedSegmentsBeforeTheNextMeasurement() {
    // given
    usedSpace.set(REPLICATION_LIMIT - 100);
    measure();
    final var allocator = monitor.limitSegmentAllocation(SegmentAllocator.noop());
    allocator.checkCanAllocate(60);

    // when - then
    assertThatThrownBy(() -> allocator.checkCanAllocate(60)).isInstanceOf(OutOfDiskSpace.class);
  }

  @Test
  void shouldAllowSegmentAllocationAgainWhenTenantUsageDrops() {
    // given
    usedSpace.set(REPLICATION_LIMIT);
    measure();
    final var allocator = monitor.limitSegmentAllocation(SegmentAllocator.noop());

    // when
    usedSpace.set(PROCESSING_LIMIT);
    measure();

    // then
    assertThatCode(() -> allocator.checkCanAllocate(100)).doesNotThrowAnyException();
  }

  @Test
  void shouldExposeUsedSpaceAndLimitsAsMetrics() {
    // given
    usedSpace.set(1234);

    // when
    measure();

    // then
    assertThat(
            meterRegistry
                .get("zeebe.broker.disk.used.bytes")
                .tag("physicalTenant", TENANT)
                .gauge()
                .value())
        .isEqualTo(1234);
    assertThat(
            meterRegistry
                .get("zeebe.broker.disk.used.limit.bytes")
                .tag("physicalTenant", TENANT)
                .tag("threshold", "processing")
                .gauge()
                .value())
        .isEqualTo(PROCESSING_LIMIT);
    assertThat(
            meterRegistry
                .get("zeebe.broker.disk.used.limit.bytes")
                .tag("physicalTenant", TENANT)
                .tag("threshold", "replication")
                .gauge()
                .value())
        .isEqualTo(REPLICATION_LIMIT);
    assertThat(
            meterRegistry
                .get("zeebe.broker.disk.used.scan.duration")
                .tag("physicalTenant", TENANT)
                .timer()
                .count())
        .isPositive();
  }

  @Test
  void shouldRemoveMetricsWhenClosed() {
    // when
    monitor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(meterRegistry.find("zeebe.broker.disk.used.bytes").meters()).isEmpty();
    assertThat(meterRegistry.find("zeebe.broker.disk.used.limit.bytes").meters()).isEmpty();
  }

  @Test
  void shouldStopListeningToVolumeWhenClosed() {
    // when
    monitor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(volumeMonitor.listeners).isEmpty();
  }

  private void measure() {
    scheduler.updateClock(INTERVAL);
    scheduler.workUntilDone();
  }

  private static final class RecordingListener implements DiskSpaceUsageListener {
    private boolean available = true;

    @Override
    public void onDiskSpaceNotAvailable() {
      available = false;
    }

    @Override
    public void onDiskSpaceAvailable() {
      available = true;
    }
  }

  private static final class StubVolumeMonitor implements DiskSpaceUsageMonitor {
    private final List<DiskSpaceUsageListener> listeners = new ArrayList<>();

    @Override
    public void addDiskUsageListener(final DiskSpaceUsageListener listener) {
      listeners.add(listener);
    }

    @Override
    public void removeDiskUsageListener(final DiskSpaceUsageListener listener) {
      listeners.remove(listener);
    }

    @Override
    public void setFreeDiskSpaceSupplier(final LongSupplier freeDiskSpaceSupplier) {}

    @Override
    public ActorFuture<Void> closeAsync() {
      return CompletableActorFuture.completed();
    }

    void notifyNotAvailable() {
      listeners.forEach(DiskSpaceUsageListener::onDiskSpaceNotAvailable);
    }

    void notifyAvailable() {
      listeners.forEach(DiskSpaceUsageListener::onDiskSpaceAvailable);
    }
  }
}
