/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.routing.DbRoutingState;
import io.camunda.zeebe.engine.state.routing.PersistedRoutingInfo;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;

public class DbMigratorImplTest {

  private static final String CURRENT_VERSION = "8.8.0";
  private MutableProcessingState mockProcessingState;
  private MigrationTaskContextImpl context;
  private final ArrayList<MigrationTask> migrations = new ArrayList<>();
  private DbMigratorImpl sut;

  @BeforeEach
  public void setup() {
    mockProcessingState = mock(MutableProcessingState.class, Answers.RETURNS_DEEP_STUBS);
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("8.7.0");
    context = new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState);
    migrations.clear();
    sut = new DbMigratorImpl(true, context, migrations, CURRENT_VERSION);
  }

  @Test
  void shouldRunMigrationThatNeedsToBeRun() {
    // given
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(context)).thenReturn(true);
    migrations.add(mockMigration);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration).runMigration(context);
  }

  @Test
  void shouldNotRunMigrationThatDoesNotNeedToBeRun() {
    // given
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(context)).thenReturn(false);
    migrations.add(mockMigration);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(context);
  }

  @Test
  void shouldRunMigrationsInOrder() {
    // given
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(context)).thenReturn(true);
    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when
    sut.runMigrations();

    // then
    final var inOrder = Mockito.inOrder(mockMigration1, mockMigration2);

    inOrder.verify(mockMigration1).runMigration(context);
    inOrder.verify(mockMigration2).runMigration(context);
  }

  @Test
  void shouldNotSetVersionIfFirstMigrationFails() {
    // given -- two migrations that both need to be run
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(context)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when -- first migration fails
    doThrow(RuntimeException.class).when(mockMigration1).runMigration(context);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);
    // then -- second migration is not run
    verify(mockMigration2, never()).runMigration(any());
    // then -- version is not set
    verify(mockProcessingState.getMigrationState(), never()).setMigratedByVersion(any());
  }

  @Test
  void shouldNotSetVersionIfSecondMigrationFails() {
    // given -- two migrations that both need to be run
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(context)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when -- second migration fails
    doThrow(RuntimeException.class).when(mockMigration2).runMigration(context);

    // then -- running migrations fails
    assertThatThrownBy(sut::runMigrations).isInstanceOf(RuntimeException.class);

    // then -- the version is not set
    verify(mockProcessingState.getMigrationState(), never()).setMigratedByVersion(any());
  }

  @Test
  void shouldSetVersionAfterRunningMigrations() {
    // given -- two migrations that both need to be run
    final var mockMigration1 = mock(MigrationTask.class);
    when(mockMigration1.needsToRun(context)).thenReturn(true);

    final var mockMigration2 = mock(MigrationTask.class);
    when(mockMigration2.needsToRun(context)).thenReturn(true);

    migrations.addAll(List.of(mockMigration1, mockMigration2));

    // when -- running migrations
    sut.runMigrations();

    // then -- the version is set
    verify(mockProcessingState.getMigrationState()).setMigratedByVersion(CURRENT_VERSION);
  }

  @Test
  void shouldThrowOnInvalidUpgradeFromMinor() {
    // given -- state that was migrated by version 1.0.0
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("1.0.0");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(any())).thenReturn(true);
    migrations.add(mockMigration);

    // when -- upgrading to a version that is not compatible
    // then -- running migrations throws
    assertThatThrownBy(sut::runMigrations)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Snapshot is not compatible with current version");
  }

  @Test
  void shouldThrowOnInvalidUpgradeFromAlpha() {
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("1.0.0");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(any())).thenReturn(true);
    migrations.add(mockMigration);
    sut = new DbMigratorImpl(true, context, migrations, "2.0.0-alpha1");

    // then -- running migrations throws
    assertThatThrownBy(sut::runMigrations)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot upgrade to or from a pre-release version");

    // then -- migration is not run
    verify(mockMigration, never())
        .runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));
  }

  @Test
  void shouldNotThrowOnInvalidUpgradeFromMinor() {
    // given -- state that was migrated by version 1.0.0
    final boolean versionCheckEnabled = false; // disable version check
    sut = new DbMigratorImpl(versionCheckEnabled, context, migrations, "2.0.0");
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("1.0.0");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(any())).thenReturn(true);
    migrations.add(mockMigration);

    // when/then -- running migrations throws
    assertThatNoException().isThrownBy(sut::runMigrations);

    verify(mockMigration, times(1))
        .runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));
  }

  @Test
  void shouldNotThrowOnInvalidUpgradeFromAlphas() {
    // given - upgrading to a pre-release version
    final boolean versionCheckEnabled = false; // disable version check
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn("1.0.0");
    final var mockMigration = mock(MigrationTask.class);
    when(mockMigration.needsToRun(any())).thenReturn(true);
    migrations.add(mockMigration);
    sut = new DbMigratorImpl(versionCheckEnabled, context, migrations, "2.0.0-alpha1");
    // when/then -- running migrations throws
    assertThatNoException().isThrownBy(sut::runMigrations);

    // then -- migration is run
    verify(mockMigration, times(1))
        .runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), mockProcessingState));
  }

  @Test
  void shouldNotRunMigrationsIfTheSameVersion() {
    // given
    // the version is the same as the migrated version
    when(mockProcessingState.getMigrationState().getMigratedByVersion())
        .thenReturn(CURRENT_VERSION);

    final var mockMigration = mock(MigrationTask.class);

    migrations.add(mockMigration);

    // when
    sut.runMigrations();

    // then
    verify(mockMigration, never()).runMigration(any());
  }

  @Test
  void shouldNotRunMigrationsWhenNoVersionIsSavedInTheState() {
    // given
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn(null);

    final var migration = mock(MigrationTask.class);
    when(migration.needsToRun(any())).thenReturn(true);
    migrations.add(migration);

    // when
    sut.runMigrations();

    // then
    verify(migration, atLeastOnce()).isInitialization();
    verify(migration, never()).runMigration(any());
    verify(mockProcessingState.getMigrationState()).setMigratedByVersion(eq(CURRENT_VERSION));
  }

  @Test
  void shouldOnlyRunInitializationMigrationsWhenDbIsEmpty() {
    // given
    when(mockProcessingState.getMigrationState().getMigratedByVersion()).thenReturn(null);

    final var migration = mock(MigrationTask.class);
    when(migration.isInitialization()).thenReturn(true);
    when(migration.needsToRun(any())).thenReturn(true);
    migrations.add(migration);

    // when
    sut.runMigrations();

    // then
    verify(migration, atLeastOnce()).isInitialization();
    verify(migration).runMigration(any());
    verify(mockProcessingState.getMigrationState()).setMigratedByVersion(eq(CURRENT_VERSION));
  }

  @Test
  void shouldRunRoutingInfoInitializationWhenDesiredIsNotSet() {
    // given
    context = new MigrationTaskContextImpl(new ClusterContextImpl(3), mockProcessingState);
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
    migrations.add(migration);
    migration.runMigration(context);

    // then
    assertThat(routingState.currentPartitions()).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(argSnapshot).containsExactly("CURRENT", "DESIRED");
  }

  @Test
  void shouldNotRunRoutingInfoInitializationWhenBothAreSet() {
    // given
    context = new MigrationTaskContextImpl(new ClusterContextImpl(3), mockProcessingState);
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
