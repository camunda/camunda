/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.engine.EngineCfg;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;

public class MigrationTransitionStepTest {

  @TempDir Path tempDir;
  ZeebeRocksDbFactory<?> factory =
      new ZeebeRocksDbFactory<ZbColumnFamilies>(
          new RocksDbConfiguration(),
          new ConsistencyChecksSettings(),
          new AccessMetricsConfiguration(Kind.NONE),
          SimpleMeterRegistry::new);

  @AutoClose ZeebeDb zeebeDb;
  TestPartitionTransitionContext context;
  DbMigrationState migrationState;

  @BeforeEach
  void setup() {
    zeebeDb = factory.createDb(tempDir.toFile());
    context = new TestPartitionTransitionContext();
    context.setZeebeDb(zeebeDb);
    final var transationContext = zeebeDb.createContext();
    migrationState = new DbMigrationState(zeebeDb, transationContext);
  }

  @Test
  public void shouldMarkMigrationDoneInContext() {
    // given
    migrationState.setMigratedByVersion("8.7.0");

    context.setBrokerCfg(new BrokerCfg());
    context.setBrokerVersion("8.8.0");
    final var step = new MigrationTransitionStep();

    // when
    step.prepareTransition(context, 0, Role.LEADER).join();
    step.transitionTo(context, 0, Role.FOLLOWER).join();

    // then
    assertThat(context.areMigrationsPerformed()).isTrue();
  }

  @Test
  public void shouldNotUnsetFlagAfterTransitioning() {
    // given
    migrationState.setMigratedByVersion("8.1.0");

    final var brokerCfg = mock(BrokerCfg.class, Answers.RETURNS_DEEP_STUBS);
    when(brokerCfg.getExperimental().getEngine()).thenReturn(new EngineCfg());
    context.setBrokerCfg(brokerCfg);
    final var step = new MigrationTransitionStep();

    // when
    step.prepareTransition(context, 0, Role.LEADER).join();
    step.transitionTo(context, 0, Role.FOLLOWER).join();
    step.prepareTransition(context, 0, Role.FOLLOWER).join();
    step.transitionTo(context, 0, Role.FOLLOWER).join();

    // then
    assertThat(context.areMigrationsPerformed()).isTrue();
  }

  @Test
  public void shouldMarkMigrationSnapshotTakenWhenAlreadyAtCurrentVersionOnBoot() {
    // given - a restart with no version change: this replica was already fully migrated in an
    // earlier boot, so no migration runs in this one either
    migrationState.setMigratedByVersion("8.8.0");
    context.setBrokerCfg(new BrokerCfg());
    context.setBrokerVersion("8.8.0");
    final var step = new MigrationTransitionStep();

    // when
    step.transitionTo(context, 0, Role.FOLLOWER).join();

    // then
    assertThat(context.areMigrationsPerformed()).isFalse();
    assertThat(context.isMigrationSnapshotTaken()).isTrue();
  }

  @Test
  public void shouldNotMarkMigrationSnapshotTakenWhenMigrationsActuallyRun() {
    // given - a genuine migration this boot; the real MigrationSnapshotDirector is responsible
    // for eventually marking the snapshot taken, not this step
    migrationState.setMigratedByVersion("8.7.0");
    context.setBrokerCfg(new BrokerCfg());
    context.setBrokerVersion("8.8.0");
    final var step = new MigrationTransitionStep();

    // when
    step.transitionTo(context, 0, Role.FOLLOWER).join();

    // then
    assertThat(context.areMigrationsPerformed()).isTrue();
    assertThat(context.isMigrationSnapshotTaken()).isFalse();
  }

  @Test
  public void shouldResetMigrationSnapshotTakenWhenAMigrationRerunsAfterAnEarlierCycle() {
    // given - an earlier migration cycle already had its snapshot taken (e.g. by the real
    // MigrationSnapshotDirector), but this replica has since been reverted to an older,
    // unmigrated version -- e.g. by installing a received snapshot from a lagging peer -- so a
    // fresh migration cycle is about to start
    migrationState.setMigratedByVersion("8.7.0");
    context.setBrokerCfg(new BrokerCfg());
    context.setBrokerVersion("8.8.0");
    context.markMigrationSnapshotTaken();
    final var step = new MigrationTransitionStep();

    // when
    step.transitionTo(context, 0, Role.FOLLOWER).join();

    // then - the stale flag from the earlier cycle must not survive into the new one
    assertThat(context.areMigrationsPerformed()).isTrue();
    assertThat(context.isMigrationSnapshotTaken()).isFalse();
  }

  @Test
  public void shouldNotMarkMigrationSnapshotTakenOnALaterTransitionWithinTheSameBoot() {
    // given - migrations already ran on an earlier transition in this same boot; a later
    // transition seeing the version already match must not short-circuit the still-pending
    // snapshot requirement for that earlier migration
    migrationState.setMigratedByVersion("8.7.0");
    context.setBrokerCfg(new BrokerCfg());
    context.setBrokerVersion("8.8.0");
    final var step = new MigrationTransitionStep();
    step.transitionTo(context, 0, Role.FOLLOWER).join();
    assertThat(context.areMigrationsPerformed()).isTrue();
    assertThat(context.isMigrationSnapshotTaken()).isFalse();

    // when - a second transition within the same boot; the version already matches now
    step.transitionTo(context, 1, Role.LEADER).join();

    // then
    assertThat(context.isMigrationSnapshotTaken()).isFalse();
  }
}
