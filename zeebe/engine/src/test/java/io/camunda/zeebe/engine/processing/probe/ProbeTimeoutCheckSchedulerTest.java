/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.probe.PersistedProbe;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.ScheduledTaskTestHarness;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Runs the {@link ProbeTimeoutCheckScheduler} on a real schedule service, whose check interval is 1
 * s: a task due further than half of it away runs only at the next check after it is due.
 */
@ExtendWith(ProcessingStateExtension.class)
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class ProbeTimeoutCheckSchedulerTest {

  private static final Duration STEP = Duration.ofMillis(50);

  @RegisterExtension
  private final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  private MutableProcessingState processingState;
  private CountingProbeState probeState;
  private ProbeTimeoutCheckScheduler scheduler;
  private ScheduledTaskTestHarness harness;

  @BeforeEach
  void setUp() {
    probeState = new CountingProbeState(processingState.getProbeState());
    scheduler = new ProbeTimeoutCheckScheduler(probeState, actorScheduler.getClock());
    harness = new ScheduledTaskTestHarness(actorScheduler, scheduler);
  }

  @Test
  void shouldTimeOutAProbeWithinAResolutionOfItsDeadline() {
    // given a deadline between two checks of the schedule service
    final long deadline = now() + 2_350;
    createProbe(1L, deadline);
    harness.recover();
    scheduler.schedule(deadline);

    // when
    advanceUntil(deadline - ProbeTimeoutCheckScheduler.TIMEOUT_RESOLUTION);

    // then
    assertThat(harness.writer().writtenIntents()).isEmpty();

    // when
    advanceUntil(deadline + ProbeTimeoutCheckScheduler.TIMEOUT_RESOLUTION);

    // then
    assertThat(harness.writer().writtenIntents()).containsExactly(ProbeIntent.TIME_OUT);
  }

  @Test
  void shouldStopWhenNoProbeIsPending() {
    // given
    final long deadline = now() + 1_000;
    createProbe(1L, deadline);
    harness.recover();
    scheduler.schedule(deadline);
    advanceUntil(deadline + ProbeTimeoutCheckScheduler.TIMEOUT_RESOLUTION);
    assertThat(harness.writer().writtenIntents()).containsExactly(ProbeIntent.TIME_OUT);
    processingState.getProbeState().remove(1L);
    final int checks = probeState.checks();

    // when
    harness.advanceTime(Duration.ofSeconds(10));

    // then
    assertThat(probeState.checks()).isEqualTo(checks);
  }

  @Test
  void shouldNotCheckWhileNoProbeWasCreated() {
    // given
    harness.recover();
    harness.advanceTime(Duration.ofSeconds(1));
    final int checks = probeState.checks();

    // when
    harness.advanceTime(Duration.ofSeconds(10));

    // then
    assertThat(probeState.checks()).isEqualTo(checks).isLessThanOrEqualTo(1);
  }

  /**
   * The checker sees only committed state. Armed before a probe's CREATE is committed, e.g. while a
   * slow log write is pending, its first check finds nothing and it stops. This is why the CREATE
   * processor schedules it in a side effect, which runs after the commit.
   */
  @Test
  void shouldTimeOutAProbeOnlyWhenScheduledAfterItIsStored() {
    // given the checker armed for a probe that is not stored yet
    final long deadline = now() + 1_500;
    harness.recover();
    scheduler.schedule(deadline);
    harness.advanceTime(Duration.ofMillis(500));
    final int checks = probeState.checks();

    // when the probe is stored after the first check
    createProbe(1L, deadline);
    harness.advanceTime(Duration.ofMillis(500));

    // then the checker has stopped and does not see it
    assertThat(probeState.checks()).isEqualTo(checks);

    // when it is scheduled again, as the side effect after the commit does
    scheduler.schedule(deadline);
    advanceUntil(deadline + ProbeTimeoutCheckScheduler.TIMEOUT_RESOLUTION);

    // then
    assertThat(harness.writer().writtenIntents()).containsExactly(ProbeIntent.TIME_OUT);
  }

  @Test
  void shouldTimeOutAfterRecoveryAProbeWhoseDeadlinePassedBefore() {
    // given a probe created on the previous leader, whose deadline passed during the leader change
    createProbe(1L, now() - 1_000);

    // when
    harness.recover();
    harness.advanceTime(Duration.ofMillis(ProbeTimeoutCheckScheduler.TIMEOUT_RESOLUTION));

    // then
    assertThat(harness.writer().writtenIntents()).containsExactly(ProbeIntent.TIME_OUT);
  }

  private void createProbe(final long probeKey, final long deadline) {
    processingState
        .getProbeState()
        .create(
            probeKey,
            new ProbeRecord().setProbeId("probe").setInputExpression("={}").setDeadline(deadline));
  }

  private void advanceUntil(final long timestamp) {
    while (now() < timestamp) {
      harness.advanceTime(STEP);
    }
  }

  private long now() {
    return actorScheduler.getClock().millis();
  }

  /** Counts how often the scheduler looks for timed-out probes. */
  private record CountingProbeState(ProbeState delegate, AtomicInteger counter)
      implements ProbeState {

    CountingProbeState(final ProbeState delegate) {
      this(delegate, new AtomicInteger());
    }

    int checks() {
      return counter.get();
    }

    @Override
    public @Nullable ProbeRecord getProbe(final long probeKey) {
      return delegate.getProbe(probeKey);
    }

    @Override
    public State getState(final long probeKey) {
      return delegate.getState(probeKey);
    }

    @Override
    public @Nullable PersistedProbe getPersistedProbe(final long probeKey) {
      return delegate.getPersistedProbe(probeKey);
    }

    @Override
    public long forEachTimedOutProbe(
        final long timestamp, final BiPredicate<Long, ProbeRecord> visitor) {
      counter.incrementAndGet();
      return delegate.forEachTimedOutProbe(timestamp, visitor);
    }

    @Override
    public void forEachActivatableProbe(
        final DirectBuffer probeId,
        final List<String> tenantIds,
        final BiPredicate<Long, ProbeRecord> visitor) {
      delegate.forEachActivatableProbe(probeId, tenantIds, visitor);
    }

    @Override
    public void forEachProbeParkedOn(
        final String storeId, final String secretReference, final LongPredicate visitor) {
      delegate.forEachProbeParkedOn(storeId, secretReference, visitor);
    }

    @Override
    public void forEachSecretReferenceParkedOn(
        final long probeKey, final BiPredicate<String, String> visitor) {
      delegate.forEachSecretReferenceParkedOn(probeKey, visitor);
    }
  }
}
