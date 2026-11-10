/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LeaseObserverTest {

  private static final Duration LEASE = Duration.ofSeconds(10);
  private static final long LEASE_NANOS = LEASE.toNanos();

  private final LeaseObserver observer = new LeaseObserver(LEASE);

  @Test
  void shouldSeeALeaseRunOutWhenItsCounterDoesNotChangeForTheLeaseDuration() {
    // given
    observer.observe(List.of(lease(1, 5, false)), 0);

    // when
    observer.observe(List.of(lease(1, 5, false)), LEASE_NANOS);

    // then
    assertThat(observer.hasRunOut(1, LEASE_NANOS)).isTrue();
  }

  @Test
  void shouldNotSeeALeaseRunOutBeforeTheLeaseDuration() {
    // given
    observer.observe(List.of(lease(1, 5, false)), 0);

    // then
    assertThat(observer.hasRunOut(1, LEASE_NANOS - 1)).isFalse();
  }

  @Test
  void shouldStartTheWaitAgainWhenTheCounterChanges() {
    // given
    observer.observe(List.of(lease(1, 5, false)), 0);

    // when - the leader renewed
    observer.observe(List.of(lease(1, 6, false)), LEASE_NANOS - 1);

    // then
    assertThat(observer.hasRunOut(1, LEASE_NANOS)).isFalse();
    assertThat(observer.hasRunOut(1, 2 * LEASE_NANOS - 1)).isTrue();
  }

  @Test
  void shouldStartTheWaitAgainWhenTheTermChanges() {
    // given
    observer.observe(List.of(lease(1, 0, false)), 0);

    // when - another node claimed, with a counter of 0 again
    observer.observe(List.of(new PartitionLease(1, "other", "west", 2, 0, false)), LEASE_NANOS);

    // then
    assertThat(observer.hasRunOut(1, LEASE_NANOS)).isFalse();
  }

  @Test
  void shouldSeeAReleasedLeaseRunOutAtOnce() {
    // when
    observer.observe(List.of(lease(1, 6, true)), 0);

    // then
    assertThat(observer.hasRunOut(1, 0)).isTrue();
  }

  @Test
  void shouldNotSeeALeaseRunOutThatItNeverObserved() {
    // then
    assertThat(observer.hasRunOut(1, LEASE_NANOS * 100)).isFalse();
  }

  @Test
  void shouldStartAllWaitsAgainAfterAFailedRead() {
    // given
    observer.observe(List.of(lease(1, 5, false)), 0);

    // when - a read failed, for example during a database failover, then one succeeds
    observer.reset();
    observer.observe(List.of(lease(1, 5, false)), LEASE_NANOS);

    // then
    assertThat(observer.hasRunOut(1, LEASE_NANOS)).isFalse();
    assertThat(observer.hasRunOut(1, 2 * LEASE_NANOS)).isTrue();
  }

  private static PartitionLease lease(final long term, final long counter, final boolean released) {
    return new PartitionLease(1, "leader", "east", term, counter, released);
  }
}
