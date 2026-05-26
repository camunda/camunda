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

import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuroraPostgresqlReplicationLogStatusProviderTest {

  @Test
  void shouldReturnCurrentAuroraPostgresqlLogStatus() {
    // given
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.getCurrentAuroraPostgresqlLogStatus()).thenReturn(123L);
    final var provider = new AuroraPostgresqlReplicationLogStatusProvider(mapper);

    // when
    final var currentLogStatus = provider.getCurrent();

    // then
    assertThat(currentLogStatus).isEqualTo(123L);
  }

  @Test
  void shouldReturnAuroraPostgresqlReplicationStatuses() {
    // given
    final var statuses = List.of(new ReplicationLogStatus(123L, "replica-1", 456L));
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.getAuroraPostgresqlReplicationStatus()).thenReturn(statuses);
    final var provider = new AuroraPostgresqlReplicationLogStatusProvider(mapper);

    // when
    final var replicationStatuses = provider.getReplicationStatuses();

    // then
    assertThat(replicationStatuses).containsExactlyElementsOf(statuses);
  }
}
