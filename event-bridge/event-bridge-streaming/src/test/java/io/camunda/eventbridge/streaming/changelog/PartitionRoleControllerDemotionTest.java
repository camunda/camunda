/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.state.TestColumnFamilies;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Demotion (ACTIVE -&gt; STANDBY): the fold releases only its own driving resources ({@link
 * Task#closeKeepingStores()}, never {@link Task#close()}), the store stays open, and a fresh {@link
 * ChangelogApplier} tails the changelog from there on — existing revocation plus starting the
 * applier from local state, per event-bridge-streaming ADR 0009 decision 6.
 */
final class PartitionRoleControllerDemotionTest {

  private static final String TOPIC = "demotion-changelog";
  private static final int PARTITION = 0;
  private static final byte[] ROW_KEY = {0, 0, 0, 4};

  @TempDir private Path storeDir;

  @Test
  void shouldDemoteWithoutClosingTheStoreAndResumeTailingFromThere() throws Exception {
    final var broker = new FakeChangelogBroker();
    final var publisher = new ChangelogPublisher(broker.client, TOPIC, PARTITION);

    try (StateStoreProvider<TestColumnFamilies> provider =
        RocksDbStateStoreProvider.<TestColumnFamilies>open(
            storeDir.toFile(), new SimpleMeterRegistry())) {

      final var controller =
          PartitionRoleController.<TestColumnFamilies, byte[]>startAsActive(
              provider,
              DemotableTask::new,
              p ->
                  ChangelogApplier.singleColumnFamily(
                      broker.client,
                      TOPIC,
                      PARTITION,
                      p,
                      TestColumnFamilies.CELLS,
                      () -> ChangelogApplier.NO_POSITION,
                      cut -> {}));

      // given — the active accumulated a row directly in its store (pre-demotion state)
      final var activeTask = (DemotableTask) controller.activeTask();
      activeTask.writeRow(ROW_KEY, "pre-demotion".getBytes());

      // when — demoted
      controller.demote();

      // then — the task released its own resources without closing the store
      assertThat(activeTask.closedKeepingStores).isTrue();
      assertThat(activeTask.fullyClosed).isFalse();
      assertThat(controller.role()).isEqualTo(PartitionRoleController.Role.STANDBY);

      // and — the pre-demotion row is still there (the store was never closed/reopened)
      assertThat(readRow(provider, ROW_KEY)).isEqualTo("pre-demotion".getBytes());

      // when — the active (now elsewhere) publishes a further cut, and this standby tails it
      publisher.publish(
          List.of(ChangelogRecord.put(new byte[] {0, 0, 0, 5}, "post-demotion".getBytes())), 99L);
      final int applied = controller.pollStandby();

      // then — the new cut lands in the very same store
      assertThat(applied).isEqualTo(1);
      assertThat(readRow(provider, new byte[] {0, 0, 0, 5})).isEqualTo("post-demotion".getBytes());
      assertThat(readRow(provider, ROW_KEY)).isEqualTo("pre-demotion".getBytes());
    }
  }

  private static byte[] readRow(
      final StateStoreProvider<TestColumnFamilies> provider, final byte[] key) {
    final var store =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final var k = new DbBytes();
    k.wrapBytes(key);
    return store.get(k).map(DbBytes::getBytes).orElse(null);
  }

  /** A {@link Task} that distinguishes a store-preserving detach from a full close. */
  private static final class DemotableTask implements Task<byte[]> {
    private final StateStoreProvider<TestColumnFamilies> provider;
    private boolean closedKeepingStores;
    private boolean fullyClosed;

    private DemotableTask(final StateStoreProvider<TestColumnFamilies> provider) {
      this.provider = provider;
    }

    @Override
    public void process(final byte[] record) {}

    @Override
    public CommitCut freezeCut(final long offset) {
      return CommitCut.NONE;
    }

    @Override
    public void close() {
      fullyClosed = true;
    }

    @Override
    public void closeKeepingStores() {
      closedKeepingStores = true;
    }

    private void writeRow(final byte[] key, final byte[] value) {
      final var store =
          provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
      final var k = new DbBytes();
      k.wrapBytes(key);
      final var v = new DbBytes();
      v.wrapBytes(value);
      store.put(k, v);
    }
  }
}
