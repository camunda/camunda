/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation;
import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import io.camunda.zeebe.engine.state.routing.PersistedRoutingInfo;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;

@ExtendWith(ProcessingStateExtension.class)
final class RoutingInfoInitializationMigrationTest {
  @SuppressWarnings("unused")
  private MutableProcessingState processingState;

  @Test
  void shouldRunMigration() {
    // given
    final var clusterContext = new ClusterContextImpl(3);

    // when
    final var migration = new RoutingInfoInitializationMigration();
    final var context = new MigrationTaskContextImpl(clusterContext, processingState);
    migration.runMigration(context);

    // then
    final var updatedRoutingState = context.processingState().getRoutingState();
    assertThat(updatedRoutingState.isInitialized()).isTrue();
  }

  @Test
  void shouldNotRunMigrationAgain() {
    // given
    final var clusterContext = new ClusterContextImpl(3);
    final var migration = new RoutingInfoInitializationMigration();
    final var context = new MigrationTaskContextImpl(clusterContext, processingState);

    // when
    migration.runMigration(context);

    // then
    assertThat(migration.needsToRun(context)).isFalse();
  }

  @Test
  void shouldInitializeRoutingInfo() {
    // given
    final var clusterContext = new ClusterContextImpl(3);
    final var migration = new RoutingInfoInitializationMigration();
    final var context = new MigrationTaskContextImpl(clusterContext, processingState);

    // when
    migration.runMigration(context);

    // then
    final var updatedRoutingState = context.processingState().getRoutingState();
    assertThat(updatedRoutingState.isInitialized()).isTrue();
    assertThat(updatedRoutingState.currentPartitions()).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(updatedRoutingState.messageCorrelation())
        .isEqualTo(new MessageCorrelation.HashMod(3));
    assertThat(updatedRoutingState.desiredPartitions()).containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  void shouldBeAnInitialization() {
    // given
    final var migration = new RoutingInfoInitializationMigration();

    // when/then
    assertThat(migration.isInitialization()).isTrue();
  }

  @Test
  void shouldRunRoutingInfoInitializationWhenDesiredIsNotSet() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class, Answers.RETURNS_DEEP_STUBS);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(3), mockProcessingState);
    final PersistedRoutingInfo persistedRoutingInfo = new PersistedRoutingInfo();
    persistedRoutingInfo.setMessageCorrelation(new HashMod(3));
    persistedRoutingInfo.setPartitions(new TreeSet<>(Set.of(1, 2, 3)));

    final var zeebeDb = mock(ZeebeDb.class);
    final var txContext = mock(TransactionContext.class);
    final ColumnFamily<DbString, PersistedRoutingInfo> routingColumnFamily =
        mock(ColumnFamily.class);
    final ColumnFamily<DbInt, DbLong> scalingStartedAtColumnFamily = mock(ColumnFamily.class);

    // mock column family creation
    when(zeebeDb.createColumnFamily(eq(ZbColumnFamilies.ROUTING), eq(txContext), any(), any()))
        .thenReturn(routingColumnFamily);
    when(zeebeDb.createColumnFamily(
            eq(ZbColumnFamilies.SCALING_STARTED_AT), eq(txContext), any(), any()))
        .thenReturn(scalingStartedAtColumnFamily);

    when(routingColumnFamily.exists(argThat(d -> d != null && d.toString().equals("CURRENT"))))
        .thenReturn(true);
    when(routingColumnFamily.exists(argThat(d -> d != null && d.toString().equals("DESIRED"))))
        .thenReturn(false);
    when(routingColumnFamily.get(argThat(d -> d != null && d.toString().equals("CURRENT"))))
        .thenReturn(persistedRoutingInfo);

    final var routingState = new DbRoutingState(zeebeDb, txContext);
    when(mockProcessingState.getRoutingState()).thenReturn(routingState);

    final var migration = new RoutingInfoInitializationMigration();

    // then the migration needs to run
    assertThat(migration.needsToRun(context)).isTrue();

    // DbString is being mutated, argument captors cannot be used to snapshot the value of the key
    // thus we use a list to snapshot the keys during invocation time to verify that both have been
    // updated
    final List<String> argSnapshot = new ArrayList<>();
    doAnswer(
            invocation -> {
              final DbString key = invocation.getArgument(0);
              argSnapshot.add(key.toString());
              return null;
            })
        .when(routingColumnFamily)
        .upsert(any(DbString.class), argThat(r -> r.getPartitions().containsAll(Set.of(1, 2, 3))));

    // when
    migration.runMigration(context);

    // then
    assertThat(routingState.currentPartitions()).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(argSnapshot).containsExactly("CURRENT", "DESIRED");
  }

  @Test
  void shouldNotRunRoutingInfoInitializationWhenBothAreSet() {
    // given
    final var mockProcessingState = mock(MutableProcessingState.class, Answers.RETURNS_DEEP_STUBS);
    final var context =
        new MigrationTaskContextImpl(new ClusterContextImpl(3), mockProcessingState);
    final var zeebeDb = mock(ZeebeDb.class);
    final var txContext = mock(TransactionContext.class);

    // mock column families
    final ColumnFamily<DbString, ?> routingColumnFamily = mock(ColumnFamily.class);
    when(zeebeDb.createColumnFamily(eq(ZbColumnFamilies.ROUTING), eq(txContext), any(), any()))
        .thenReturn(routingColumnFamily);
    when(routingColumnFamily.exists(any())).thenReturn(true);

    final var routingState = new DbRoutingState(zeebeDb, txContext);

    when(mockProcessingState.getRoutingState()).thenReturn(routingState);

    final var migration = new RoutingInfoInitializationMigration();
    assertThat(migration.needsToRun(context)).isFalse();
  }
}
