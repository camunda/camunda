/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class RaftPartitionLeadershipTest {

  private final RaftPartition raft = mock(RaftPartition.class);
  private final RaftPartitionLeadership leadership = new RaftPartitionLeadership(raft);

  @Test
  void shouldPassTheRolesOfRaftToTheListener() {
    // given
    final var roles = new ArrayList<String>();
    leadership.addListener((role, term) -> roles.add(role + "@" + term));
    final var captor = ArgumentCaptor.forClass(RaftRoleChangeListener.class);
    verify(raft).addRoleChangeListener(captor.capture());

    // when
    captor.getValue().onNewRole(Role.LEADER, 3);

    // then
    assertThat(roles).containsExactly("LEADER@3");
  }

  @Test
  void shouldRemoveTheSameListenerFromRaftThatItAdded() {
    // given
    final PartitionLeadershipListener listener = (role, term) -> {};
    leadership.addListener(listener);
    final var added = ArgumentCaptor.forClass(RaftRoleChangeListener.class);
    verify(raft).addRoleChangeListener(added.capture());

    // when - another instance, as a context could create
    new RaftPartitionLeadership(raft).removeListener(listener);

    // then - Raft keeps its listeners in a set, so equal is enough
    final var removed = ArgumentCaptor.forClass(RaftRoleChangeListener.class);
    verify(raft).removeRoleChangeListener(removed.capture());
    assertThat(removed.getValue()).isEqualTo(added.getValue());
  }

  @Test
  void shouldTakeRoleAndTermFromRaft() {
    // given
    when(raft.getRole()).thenReturn(Role.FOLLOWER);
    when(raft.term()).thenReturn(7L);

    // then
    assertThat(leadership.role()).isEqualTo(Role.FOLLOWER);
    assertThat(leadership.term()).isEqualTo(7L);
  }

  @Test
  void shouldStepDownThroughRaft() {
    // given
    final var done = CompletableFuture.<Void>completedFuture(null);
    when(raft.stepDown()).thenReturn(done);

    // when
    final var result = leadership.stepDown();

    // then
    assertThat(result).isSameAs(done);
  }
}
