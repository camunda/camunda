/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import org.junit.jupiter.api.AfterEach;
import java.util.ArrayList;
import java.time.Duration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiver;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogArchiveTransitionStep.FanOutListener;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogArchiveTransitionStepTest {

  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();

  @TempDir Path storeDirectory;
  @TempDir Path partitionDirectory;

  private final TestPartitionTransitionContext context = new TestPartitionTransitionContext();
  private final BrokerCfg brokerCfg = new BrokerCfg();
  private final ActorSchedulingService actorSchedulingService = mock(ActorSchedulingService.class);
  private final AtomixLogStorage logStorage = mock(AtomixLogStorage.class);
  private final LogArchiveTransitionStep step = new LogArchiveTransitionStep();
  private final List<ZeebeDb<ZbColumnFamilies>> dbs = new ArrayList<>();

  @BeforeEach
  void setUp() {
    final var backupCfg = brokerCfg.getData().getBackup();
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.getFilesystem().setBasePath(storeDirectory.toString());
    backupCfg.getLogArchive().setEnabled(true);

    final var raftPartition = mock(RaftPartition.class);
    final var server = mock(RaftPartitionServer.class);
    final var logStream = mock(LogStream.class);
    lenient().when(raftPartition.dataDirectory()).thenReturn(partitionDirectory.toFile());
    lenient().when(raftPartition.getServer()).thenReturn(server);
    lenient().when(server.openReader()).thenReturn(mock(RaftLogReader.class));
    lenient().when(logStream.newLogStreamWriter()).thenReturn(mock(LogStreamWriter.class));
    lenient()
        .when(actorSchedulingService.submitActor(any(), any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    context.setConcurrencyControl(CONCURRENCY_CONTROL);
    context.setActorSchedulingService(actorSchedulingService);
    context.setBrokerCfg(brokerCfg);
    context.setRaftPartition(raftPartition);
    context.setLogStream(logStream);
    context.setLogStorage(logStorage);
  }

  @Test
  void shouldNotInstallAnythingWhenDisabled() {
    // given
    brokerCfg.getData().getBackup().getLogArchive().setEnabled(false);

    // when
    step.transitionTo(context, 1, Role.LEADER).join();

    // then
    assertThat(context.getLogArchiveProcessor()).isNull();
    verifyNoInteractions(actorSchedulingService);
  }

  @Test
  void shouldRefuseToStartWhenDisabledAfterHandover() {
    // given
    brokerCfg.getData().getBackup().getLogArchive().setEnabled(false);
    final var zeebeDb = createDb();
    new DbLogArchiveState(zeebeDb, zeebeDb.createContext())
        .setMeta(new LogArchiveMeta().setHandoverCheckpointId(42L).setHandoverPosition(420L));
    context.setZeebeDb(zeebeDb);

    // when
    final var future = step.transitionTo(context, 1, Role.FOLLOWER);

    // then
    assertThat(future)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("cannot be disabled after the handover")
        .withMessageContaining("42");
  }

  @Test
  void shouldStartWhenDisabledAndNoHandoverWasRecorded() {
    // given
    brokerCfg.getData().getBackup().getLogArchive().setEnabled(false);
    context.setZeebeDb(createDb());

    // when
    final var future = step.transitionTo(context, 1, Role.LEADER);

    // then
    assertThat(future.isCompletedExceptionally()).isFalse();
    assertThat(context.getLogArchiveProcessor()).isNull();
  }

  private ZeebeDb<ZbColumnFamilies> createDb() {
    final var zeebeDb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE),
                SimpleMeterRegistry::new)
            .createDb(partitionDirectory.resolve("db").toFile());
    dbs.add(zeebeDb);
    return zeebeDb;
  }

  @AfterEach
  void closeDbs() throws Exception {
    for (final var db : dbs) {
      db.close();
    }
  }

  @Test
  void shouldInstallOnlyTheProcessorOnFollower() {
    // when
    step.transitionTo(context, 1, Role.FOLLOWER).join();

    // then
    assertThat(context.getLogArchiveProcessor()).isNotNull();
    verifyNoInteractions(actorSchedulingService);
    verifyNoInteractions(logStorage);
  }

  @Test
  void shouldStartLeaderComponentsOnLeader() {
    // when
    step.transitionTo(context, 3, Role.LEADER).join();

    // then
    assertThat(context.getLogArchiveProcessor()).isNotNull();
    verify(actorSchedulingService, times(3)).submitActor(any(), any());
    verify(logStorage).addCommitListener(any(LogArchiver.class));
  }

  @Test
  void shouldFailLeaderTransitionForUnsupportedStore() {
    // given
    brokerCfg.getData().getBackup().setStore(BackupStoreType.GCS);

    // when
    final var future = step.transitionTo(context, 3, Role.LEADER);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    verifyNoInteractions(actorSchedulingService);
  }

  @Test
  void shouldRemoveProcessorWhenTransitioning() {
    // given
    step.transitionTo(context, 1, Role.FOLLOWER).join();
    context.setCurrentRole(Role.FOLLOWER);

    // when
    step.prepareTransition(context, 2, Role.LEADER).join();

    // then
    assertThat(context.getLogArchiveProcessor()).isNull();
  }

  @Test
  void shouldKeepProcessorFromFollowerToCandidate() {
    // given
    step.transitionTo(context, 1, Role.FOLLOWER).join();
    final var processor = context.getLogArchiveProcessor();
    context.setCurrentRole(Role.FOLLOWER);

    // when
    step.prepareTransition(context, 2, Role.CANDIDATE).join();
    step.transitionTo(context, 2, Role.CANDIDATE).join();

    // then
    assertThat(context.getLogArchiveProcessor()).isSameAs(processor);
  }

  @Test
  void shouldForwardEveryListenerMethod() {
    // given
    final var listenerMethods =
        Arrays.stream(LogArchiveListener.class.getMethods())
            .filter(m -> !Modifier.isStatic(m.getModifiers()))
            .toList();

    // then - a new default method on the listener must not be silently swallowed
    for (final Method method : listenerMethods) {
      assertThat(declares(method))
          .describedAs("FanOutListener must override %s", method.getName())
          .isTrue();
    }
  }

  @Test
  void shouldDeliverRecoveredMetaToAllDelegates() {
    // given
    final var first = mock(LogArchiveListener.class);
    final var second = mock(LogArchiveListener.class);
    final var fanOut = new FanOutListener(List.of(first, second));
    final var meta = new LogArchiveMeta().setTimelineId("tl-0").setArchivedIndex(5);

    // when
    fanOut.onRecovered(meta);
    fanOut.onLogArchived(7, 70);

    // then
    verify(first).onRecovered(argThat(m -> m.getArchivedIndex() == 5));
    verify(second).onRecovered(argThat(m -> m.getArchivedIndex() == 5));
    verify(first).onLogArchived(7, 70);
    verify(second).onLogArchived(7, 70);
  }

  private static boolean declares(final Method method) {
    try {
      FanOutListener.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
      return true;
    } catch (final NoSuchMethodException e) {
      return false;
    }
  }
}
