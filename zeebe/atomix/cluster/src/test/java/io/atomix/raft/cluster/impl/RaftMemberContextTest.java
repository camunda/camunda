/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.cluster.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.storage.log.RaftLog;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class RaftMemberContextTest {

  @Test
  void shouldStartWithZeroSnapshotReplicationLag() {
    // given
    final var context = newContext();

    // then
    assertThat(context.getSnapshotReplicationLag()).isZero();
    assertThat(context.getReplicationLagBytes()).isZero();
  }

  @Test
  void shouldSetAndSubtractSnapshotReplicationLag() {
    // given
    final var context = newContext();

    // when
    context.setSnapshotReplicationLag(1000);
    context.subtractSnapshotReplicationLag(300);

    // then
    assertThat(context.getSnapshotReplicationLag()).isEqualTo(700);
    assertThat(context.getReplicationLagBytes()).isEqualTo(700);
  }

  @Test
  void shouldFloorSnapshotReplicationLagAtZero() {
    // given
    final var context = newContext();
    context.setSnapshotReplicationLag(100);

    // when
    context.subtractSnapshotReplicationLag(500);

    // then
    assertThat(context.getSnapshotReplicationLag()).isZero();
    assertThat(context.getReplicationLagBytes()).isZero();
  }

  @Test
  void shouldRememberInFlightChunkBytes() {
    // given
    final var context = newContext();

    // when
    context.setSnapshotChunkBytesInFlight(512);

    // then
    assertThat(context.getSnapshotChunkBytesInFlight()).isEqualTo(512);
  }

  @Test
  void shouldClearSnapshotReplicationStateOnResetState() {
    // given
    final var log = mock(RaftLog.class);
    final var context = newContext();
    context.setSnapshotReplicationLag(5678);
    context.setSnapshotChunkBytesInFlight(512);

    // when
    context.resetState(log);

    // then
    assertThat(context.getSnapshotReplicationLag()).isZero();
    assertThat(context.getSnapshotChunkBytesInFlight()).isZero();
    assertThat(context.getReplicationLagBytes()).isZero();
  }

  private RaftMemberContext newContext() {
    final var member = new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.now());
    return new RaftMemberContext(member, mock(RaftClusterContext.class), 1);
  }
}
