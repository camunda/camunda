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

class AzureGeoReplicationLagProviderTest {

  @Test
  void shouldDelegateCurrentDbTimeToMapper() {
    // given
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.getAzureCurrentDbTime()).thenReturn(42L);
    final var provider = new AzureGeoReplicationLagProvider(mapper);

    // when
    final var currentDbTime = provider.getCurrentDbTime();

    // then
    assertThat(currentDbTime).isEqualTo(42L);
  }

  @Test
  void shouldDelegateReplicationStatusesToMapper() {
    // given
    final var mapper = mock(ReplicationStatusMapper.class);
    when(mapper.getAzureGeoReplicationStatus())
        .thenReturn(List.of(new ReplicationLagStatus("replica-1", 1_000L, 5_000L)));
    final var provider = new AzureGeoReplicationLagProvider(mapper);

    // when
    final var statuses = provider.getReplicationStatuses();

    // then
    assertThat(statuses).containsExactly(new ReplicationLagStatus("replica-1", 1_000L, 5_000L));
  }
}
