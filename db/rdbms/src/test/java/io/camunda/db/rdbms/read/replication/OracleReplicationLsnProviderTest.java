/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import java.sql.SQLException;
import java.util.List;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.Test;

class OracleReplicationLsnProviderTest {

  @Test
  void shouldKeepExactScnWhenTimingIsAvailable() {
    // given
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.getReplicationStatus())
        .thenReturn(List.of(new ReplicationLsnStatus(100L, "replica-1", null, null)));
    when(mapper.getOracleReplicationStatusWithTiming())
        .thenReturn(List.of(new ReplicationLsnStatus(999L, "replica-1", 3_000L, 1_000L)));
    final var provider = new OracleReplicationLsnProvider(mapper);

    // when
    final var statuses = provider.getReplicationStatuses();

    // then
    assertThat(statuses)
        .containsExactly(new ReplicationLsnStatus(100L, "replica-1", 3_000L, 1_000L));
  }

  @Test
  void shouldKeepExactScnWhenTimestampMappingHasExpired() {
    // given
    final var mapper = mock(ReplicationStatusMapper.class);
    final var exactStatus = new ReplicationLsnStatus(100L, "replica-1", null, null);
    when(mapper.getReplicationStatus()).thenReturn(List.of(exactStatus));
    when(mapper.getOracleReplicationStatusWithTiming())
        .thenThrow(
            new PersistenceException(
                new SQLException(
                    "ORA-08181: specified number is not a valid system change number")));
    final var provider = new OracleReplicationLsnProvider(mapper);

    // when
    final var statuses = provider.getReplicationStatuses();

    // then
    assertThat(statuses).containsExactly(exactStatus);
  }

  @Test
  void shouldPropagateUnexpectedTimingErrors() {
    // given
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.getReplicationStatus())
        .thenReturn(List.of(new ReplicationLsnStatus(100L, "replica-1", null, null)));
    final var exception = new PersistenceException("unexpected database error");
    when(mapper.getOracleReplicationStatusWithTiming()).thenThrow(exception);
    final var provider = new OracleReplicationLsnProvider(mapper);

    // when / then
    assertThatThrownBy(provider::getReplicationStatuses).isSameAs(exception);
  }
}
