/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.offset;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.broker.offset.OffsetStore.ConsumerKey;
import io.camunda.eventbridge.broker.offset.OffsetStore.OffsetEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OffsetStoreTest {

  private OffsetStore store;

  @BeforeEach
  void setUp() {
    store = new OffsetStore();
  }

  // -------------------------------------------------------------------------
  // commit / getCommittedOffset

  @Nested
  class CommitAndGet {

    @Test
    void shouldReturnMinusOneWhenNoCommit() {
      // given / when / then
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(-1L);
    }

    @Test
    void shouldReturnCommittedPosition() {
      // given
      store.commit("g1", "c1", 0, 42L);

      // when / then
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(42L);
    }

    @Test
    void shouldAdvanceOffsetWhenHigherPositionCommitted() {
      // given
      store.commit("g1", "c1", 0, 10L);

      // when
      store.commit("g1", "c1", 0, 20L);

      // then
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(20L);
    }

    @Test
    void shouldBeIdempotentForLowerOrEqualPosition() {
      // given
      store.commit("g1", "c1", 0, 50L);

      // when — commit with lower and equal value
      store.commit("g1", "c1", 0, 30L);
      store.commit("g1", "c1", 0, 50L);

      // then — stored value unchanged
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(50L);
    }

    @Test
    void shouldTrackOffsetsPerPartitionIndependently() {
      // given
      store.commit("g1", "c1", 0, 100L);
      store.commit("g1", "c1", 1, 200L);

      // then
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(100L);
      assertThat(store.getCommittedOffset("g1", "c1", 1)).isEqualTo(200L);
    }

    @Test
    void shouldTrackOffsetsPerConsumerIndependently() {
      // given
      store.commit("g1", "c1", 0, 100L);
      store.commit("g1", "c2", 0, 200L);

      // then
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(100L);
      assertThat(store.getCommittedOffset("g1", "c2", 0)).isEqualTo(200L);
    }

    @Test
    void shouldTrackOffsetsPerGroupIndependently() {
      // given
      store.commit("g1", "c1", 0, 100L);
      store.commit("g2", "c1", 0, 999L);

      // then
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(100L);
      assertThat(store.getCommittedOffset("g2", "c1", 0)).isEqualTo(999L);
    }
  }

  // -------------------------------------------------------------------------
  // getTruncationBoundary

  @Nested
  class TruncationBoundary {

    @Test
    void shouldReturnMaxValueWhenNoEligibleConsumers() {
      // given — empty store
      final Set<ConsumerKey> alive = Set.of(new ConsumerKey("g1", "c1"));

      // when / then
      assertThat(store.getTruncationBoundary(0, alive)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldReturnMaxValueWhenEligibleSetIsEmpty() {
      // given
      store.commit("g1", "c1", 0, 100L);

      // when / then — empty alive set
      assertThat(store.getTruncationBoundary(0, Set.of())).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldReturnSingleConsumerOffset() {
      // given
      store.commit("g1", "c1", 0, 100L);
      final Set<ConsumerKey> alive = Set.of(new ConsumerKey("g1", "c1"));

      // when / then
      assertThat(store.getTruncationBoundary(0, alive)).isEqualTo(100L);
    }

    @Test
    void shouldReturnMinAcrossMultipleConsumers() {
      // given
      store.commit("g1", "c1", 0, 300L);
      store.commit("g1", "c2", 0, 100L);
      store.commit("g1", "c3", 0, 200L);
      final Set<ConsumerKey> alive =
          Set.of(
              new ConsumerKey("g1", "c1"),
              new ConsumerKey("g1", "c2"),
              new ConsumerKey("g1", "c3"));

      // when / then
      assertThat(store.getTruncationBoundary(0, alive)).isEqualTo(100L);
    }

    @Test
    void shouldExcludeDeadConsumerFromMinCalculation() {
      // given — c1 alive at 50, c2 dead (not in eligible set) at 10
      store.commit("g1", "c1", 0, 50L);
      store.commit("g1", "c2", 0, 10L);
      final Set<ConsumerKey> alive = Set.of(new ConsumerKey("g1", "c1"));

      // when / then — c2's offset (10) must not block truncation
      assertThat(store.getTruncationBoundary(0, alive)).isEqualTo(50L);
    }

    @Test
    void shouldReturnMaxValueWhenEligibleConsumersHaveNoCommitsForPartition() {
      // given — consumers exist but committed to a different partition
      store.commit("g1", "c1", 1, 100L);
      final Set<ConsumerKey> alive = Set.of(new ConsumerKey("g1", "c1"));

      // when / then — partition 0 has no commits from alive consumers
      assertThat(store.getTruncationBoundary(0, alive)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldRetainOffsetAfterConsumerIsRemovedFromAliveSet() {
      // given — consumer commits, is then removed from the alive set (simulating eviction),
      // and later re-subscribes (re-added to alive set).
      // The OffsetStore must retain the offset through eviction so that on re-join the consumer
      // can resume from its last committed position and re-enters the truncation calculation.
      store.commit("g1", "c1", 0, 75L);

      // Eviction is tracked externally (ConsumerGroupRegistry / CoordinatorActor).
      // The OffsetStore itself never removes entries — it is the offset's safe haven.
      // Simulate re-join: consumer is back in the alive set.
      final Set<ConsumerKey> aliveAfterRejoin = Set.of(new ConsumerKey("g1", "c1"));

      // when / then — offset is still present; truncation boundary reflects it
      assertThat(store.getTruncationBoundary(0, aliveAfterRejoin)).isEqualTo(75L);
    }
  }

  // -------------------------------------------------------------------------
  // getEntriesForPartition / getAllEntries

  @Nested
  class EntryInspection {

    @Test
    void shouldReturnEmptyListWhenNoEntries() {
      assertThat(store.getEntriesForPartition(0)).isEmpty();
      assertThat(store.getAllEntries()).isEmpty();
    }

    @Test
    void shouldReturnEntriesForSpecificPartition() {
      // given
      store.commit("g1", "c1", 0, 10L);
      store.commit("g1", "c1", 1, 20L);
      store.commit("g2", "c2", 0, 30L);

      // when
      final var entries = store.getEntriesForPartition(0);

      // then
      assertThat(entries)
          .hasSize(2)
          .containsExactlyInAnyOrder(
              new OffsetEntry("g1", "c1", 0, 10L), new OffsetEntry("g2", "c2", 0, 30L));
    }

    @Test
    void shouldReturnAllEntries() {
      // given
      store.commit("g1", "c1", 0, 10L);
      store.commit("g1", "c1", 1, 20L);

      // when
      final var all = store.getAllEntries();

      // then
      assertThat(all)
          .hasSize(2)
          .containsExactlyInAnyOrder(
              new OffsetEntry("g1", "c1", 0, 10L), new OffsetEntry("g1", "c1", 1, 20L));
    }
  }

  // -------------------------------------------------------------------------
  // Snapshot serialization — serializeForPartition / deserializeForPartition

  @Nested
  class Serialization {

    @Test
    void shouldNotClearExistingDataWhenNullDataIsDeserialized() {
      // given — existing committed offset
      store.commit("g1", "c1", 0, 42L);

      // when — deserialize with null (e.g. a failed snapshot read)
      store.deserializeForPartition(0, null);

      // then — existing offset must be preserved (not wiped)
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(42L);
    }

    @Test
    void shouldNotClearExistingDataWhenEmptyDataIsDeserialized() {
      // given
      store.commit("g1", "c1", 0, 99L);

      // when
      store.deserializeForPartition(0, new byte[0]);

      // then
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(99L);
    }

    @Test
    void shouldSerializeAndDeserializeEmptyPartition() {
      // given — no entries for partition 0
      final byte[] bytes = store.serializeForPartition(0);

      // when — deserialize into a new store
      final var restored = new OffsetStore();
      restored.deserializeForPartition(0, bytes);

      // then
      assertThat(restored.getEntriesForPartition(0)).isEmpty();
    }

    @Test
    void shouldRoundTripSingleEntry() {
      // given
      store.commit("g1", "c1", 0, 42L);
      final byte[] bytes = store.serializeForPartition(0);

      // when
      final var restored = new OffsetStore();
      restored.deserializeForPartition(0, bytes);

      // then
      assertThat(restored.getCommittedOffset("g1", "c1", 0)).isEqualTo(42L);
    }

    @Test
    void shouldRoundTripMultipleEntries() {
      // given
      store.commit("group-a", "consumer-1", 2, 100L);
      store.commit("group-a", "consumer-2", 2, 200L);
      store.commit("group-b", "consumer-1", 2, 300L);
      final byte[] bytes = store.serializeForPartition(2);

      // when
      final var restored = new OffsetStore();
      restored.deserializeForPartition(2, bytes);

      // then
      assertThat(restored.getCommittedOffset("group-a", "consumer-1", 2)).isEqualTo(100L);
      assertThat(restored.getCommittedOffset("group-a", "consumer-2", 2)).isEqualTo(200L);
      assertThat(restored.getCommittedOffset("group-b", "consumer-1", 2)).isEqualTo(300L);
    }

    @Test
    void shouldOnlySerializeEntriesForTargetPartition() {
      // given — commits on partition 0 and 1
      store.commit("g1", "c1", 0, 10L);
      store.commit("g1", "c1", 1, 20L);
      final byte[] bytes = store.serializeForPartition(0);

      // when — restore partition 0 only
      final var restored = new OffsetStore();
      restored.deserializeForPartition(0, bytes);

      // then — partition 0 restored, partition 1 absent
      assertThat(restored.getCommittedOffset("g1", "c1", 0)).isEqualTo(10L);
      assertThat(restored.getCommittedOffset("g1", "c1", 1)).isEqualTo(-1L);
    }

    @Test
    void shouldReplaceExistingPartitionEntriesOnDeserialize() {
      // given — existing stale data for partition 0
      store.commit("g1", "c1", 0, 999L);
      final byte[] snapshotBytes;
      {
        final var snapshot = new OffsetStore();
        snapshot.commit("g1", "c1", 0, 50L);
        snapshotBytes = snapshot.serializeForPartition(0);
      }

      // when — deserialize overwrites
      store.deserializeForPartition(0, snapshotBytes);

      // then — stale value replaced
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(50L);
    }

    @Test
    void shouldTolerateTruncationBoundaryAfterRoundTrip() {
      // given
      store.commit("g1", "c1", 0, 77L);
      store.commit("g1", "c2", 0, 33L);
      final byte[] bytes = store.serializeForPartition(0);

      final var restored = new OffsetStore();
      restored.deserializeForPartition(0, bytes);

      // when
      final Set<ConsumerKey> alive =
          Set.of(new ConsumerKey("g1", "c1"), new ConsumerKey("g1", "c2"));

      // then
      assertThat(restored.getTruncationBoundary(0, alive)).isEqualTo(33L);
    }
  }

  // -------------------------------------------------------------------------
  // Snapshot file I/O — saveToDirectory / loadFromDirectory

  @Nested
  class FileIO {

    @Test
    void shouldSaveAndLoadSnapshotFile(@TempDir final Path tmpDir) throws IOException {
      // given
      store.commit("g1", "c1", 0, 123L);

      // when
      store.saveToDirectory(0, tmpDir);
      assertThat(tmpDir.resolve(OffsetStore.SNAPSHOT_FILE_NAME)).exists();

      // then — load into fresh store
      final var restored = new OffsetStore();
      restored.loadFromDirectory(0, tmpDir);
      assertThat(restored.getCommittedOffset("g1", "c1", 0)).isEqualTo(123L);
    }

    @Test
    void shouldHandleMissingSnapshotFileGracefully(@TempDir final Path tmpDir) throws IOException {
      // given — no file written
      // when
      store.loadFromDirectory(0, tmpDir);

      // then — store is still empty, no exception
      assertThat(store.getAllEntries()).isEmpty();
    }

    @Test
    void shouldPreserveOtherPartitionDataOnLoad(@TempDir final Path tmpDir) throws IOException {
      // given — pre-load state for partition 1
      store.commit("g1", "c1", 1, 500L);

      // and — snapshot file for partition 0 only
      final var src = new OffsetStore();
      src.commit("g1", "c1", 0, 42L);
      src.saveToDirectory(0, tmpDir);

      // when — load partition 0 into store that already has partition 1 data
      store.loadFromDirectory(0, tmpDir);

      // then — both partitions present
      assertThat(store.getCommittedOffset("g1", "c1", 0)).isEqualTo(42L);
      assertThat(store.getCommittedOffset("g1", "c1", 1)).isEqualTo(500L);
    }

    @Test
    void shouldProduceNonEmptyFile(@TempDir final Path tmpDir) throws IOException {
      // given
      store.commit("g1", "c1", 0, 1L);
      store.saveToDirectory(0, tmpDir);

      // then
      assertThat(Files.size(tmpDir.resolve(OffsetStore.SNAPSHOT_FILE_NAME))).isGreaterThan(0L);
    }
  }
}
