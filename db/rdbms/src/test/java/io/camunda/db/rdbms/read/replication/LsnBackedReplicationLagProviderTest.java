/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class LsnBackedReplicationLagProviderTest {

  @Test
  void shouldMapLsnStatusesToLagStatusesDroppingLogStatus() {
    // given
    final var lsnProvider = mock(ReplicationLsnProvider.class);
    when(lsnProvider.getReplicationStatuses())
        .thenReturn(
            List.of(
                new ReplicationLsnStatus(10L, "replica-1", 1_000L, 5_000L),
                new ReplicationLsnStatus(20L, "replica-2", 2_000L, 6_000L)));
    final var lagProvider = new LsnBackedReplicationLagProvider(lsnProvider);

    // when
    final var statuses = lagProvider.getReplicationStatuses();

    // then - logStatus is dropped, but replicationLagMs and the as-of point carry through
    assertThat(statuses)
        .containsExactly(
            new ReplicationLagStatus("replica-1", 1_000L, 5_000L),
            new ReplicationLagStatus("replica-2", 2_000L, 6_000L));
  }

  @Test
  void shouldReturnEmptyListWhenNoReplicasConnected() {
    // given
    final var lsnProvider = mock(ReplicationLsnProvider.class);
    when(lsnProvider.getReplicationStatuses()).thenReturn(List.of());
    final var lagProvider = new LsnBackedReplicationLagProvider(lsnProvider);

    // when
    final var statuses = lagProvider.getReplicationStatuses();

    // then
    assertThat(statuses).isEmpty();
  }
}
