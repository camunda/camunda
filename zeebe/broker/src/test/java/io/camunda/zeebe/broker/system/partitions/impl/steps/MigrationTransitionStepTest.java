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
import io.camunda.zeebe.engine.common.state.migration.DbMigrationState;
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
          new AccessMetricsConfiguration(Kind.NONE, 1),
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
}
