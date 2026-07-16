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
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Promotion (event-bridge-streaming ADR 0009 decision 6 / consumer-groups ADR 0006 decision 1): a
 * caught-up standby's changelog applier drains to the last marker, hands its still-open store to
 * the fold, and the fold's own {@link Task#restore()} resumes exactly where the applier left off —
 * the marker's source offset, never a consumer group's committed offset (the marker is always at
 * least as fresh, per {@link ChangelogApplier}'s resume-authority javadoc).
 */
final class PartitionRoleControllerPromotionTest {

  private static final String TOPIC = "promotion-changelog";
  private static final int PARTITION = 0;
  private static final byte[] OFFSET_KEY = {0, 0, 0, 9};

  @TempDir private Path storeDir;

  @Test
  void shouldPromoteACaughtUpStandbyAndResumeTheFoldFromTheMarkersOffsetNotTheGroupOffset()
      throws Exception {
    final var broker = new FakeChangelogBroker();
    final var publisher = new ChangelogPublisher(broker.client, TOPIC, PARTITION);

    // given — the active published two cuts, the last carrying source offset 30
    publisher.publish(List.of(ChangelogRecord.put(new byte[] {0, 0, 0, 1}, "a".getBytes())), 10L);
    publisher.publish(
        List.of(ChangelogRecord.put(new byte[] {0, 0, 0, 1}, "a-updated".getBytes())), 30L);

    // a stale consumer-group committed offset, deliberately behind the marker's source offset —
    // proves promotion never consults it
    final long groupCommittedOffset = 5L;

    try (StateStoreProvider<TestColumnFamilies> provider =
        RocksDbStateStoreProvider.<TestColumnFamilies>open(
            storeDir.toFile(), new SimpleMeterRegistry())) {

      final var controller =
          PartitionRoleController.<TestColumnFamilies, byte[]>startAsStandby(
              provider,
              FakeTask::new,
              p ->
                  ChangelogApplier.singleColumnFamily(
                      broker.client,
                      TOPIC,
                      PARTITION,
                      p,
                      TestColumnFamilies.CELLS,
                      () -> FakeTask.readOffset(p),
                      cut -> FakeTask.writeOffset(p, cut.sourceOffset())));

      // sanity: nothing caught up yet is visible as ACTIVE
      assertThat(controller.role()).isEqualTo(PartitionRoleController.Role.STANDBY);

      // when — promoted
      final long restoredOffset = controller.promote();

      // then — the fold resumes from the marker's source offset (30), not the stale group offset
      assertThat(controller.role()).isEqualTo(PartitionRoleController.Role.ACTIVE);
      assertThat(restoredOffset).isEqualTo(30L).isNotEqualTo(groupCommittedOffset);

      // and — the fold sees the exact same store the standby just replicated into (the row the
      // second cut updated is visible to the newly constructed task, proving store handoff, not a
      // fresh empty store)
      final var task = (FakeTask) controller.activeTask();
      assertThat(task.readRow(new byte[] {0, 0, 0, 1})).isEqualTo("a-updated".getBytes());

      // and — the fold can keep processing new records as though nothing special happened
      task.process("next-record".getBytes());
      assertThat(task.processed()).containsExactly("next-record".getBytes());
    }
  }

  /** A minimal {@link Task} that stores its own restore baseline in the handed-off provider. */
  private static final class FakeTask implements Task<byte[]> {
    private final StateStoreProvider<TestColumnFamilies> provider;
    private final List<byte[]> processed = new ArrayList<>();

    private FakeTask(final StateStoreProvider<TestColumnFamilies> provider) {
      this.provider = provider;
    }

    @Override
    public void process(final byte[] record) {
      processed.add(record);
    }

    @Override
    public long restore() {
      final var offset = readOffset(provider);
      return offset;
    }

    @Override
    public CommitCut freezeCut(final long offset) {
      return CommitCut.NONE;
    }

    @Override
    public void closeKeepingStores() {
      // Built around an externally owned provider — nothing of its own to release, and the
      // provider must stay open for the demoted applier.
    }

    private byte[] readRow(final byte[] key) {
      final var store =
          provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
      final var k = new DbBytes();
      k.wrapBytes(key);
      return store.get(k).map(DbBytes::getBytes).orElse(null);
    }

    private List<byte[]> processed() {
      return processed;
    }

    private static long readOffset(final StateStoreProvider<TestColumnFamilies> provider) {
      final var store =
          provider.keyValueStore(TestColumnFamilies.OFFSETS, new DbBytes(), new DbBytes());
      final var k = new DbBytes();
      k.wrapBytes(OFFSET_KEY);
      return store.get(k).map(v -> ByteBuffer.wrap(v.getBytes()).getLong()).orElse(Task.NO_OFFSET);
    }

    private static void writeOffset(
        final StateStoreProvider<TestColumnFamilies> provider, final long offset) {
      final var store =
          provider.keyValueStore(TestColumnFamilies.OFFSETS, new DbBytes(), new DbBytes());
      final var k = new DbBytes();
      k.wrapBytes(OFFSET_KEY);
      final var v = new DbBytes();
      v.wrapBytes(ByteBuffer.allocate(8).putLong(offset).array());
      store.put(k, v);
    }
  }
}
