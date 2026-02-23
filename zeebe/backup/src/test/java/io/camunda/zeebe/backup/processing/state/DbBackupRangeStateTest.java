/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.processing.state.DbBackupRangeState.BackupRange;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DbBackupRangeStateTest {

  @TempDir Path database;
  private ZeebeDb<ZbColumnFamilies> zeebedb;
  private DbBackupRangeState state;

  @BeforeEach
  void before() {
    zeebedb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(database.toFile());
    state = new DbBackupRangeState(zeebedb, zeebedb.createContext());
  }

  @AfterEach
  void closeDb() throws Exception {
    zeebedb.close();
  }

  // --- startNewRange ---

  @Test
  void shouldStartNewRange() {
    // when
    state.startNewRange(5L);

    // then
    final var ranges = state.getAllRanges();
    assertThat(ranges).containsExactly(new BackupRange(5L, 5L));
  }

  @Test
  void shouldStartMultipleRanges() {
    // when
    state.startNewRange(1L);
    state.startNewRange(10L);

    // then
    final var ranges = state.getAllRanges();
    assertThat(ranges).containsExactly(new BackupRange(1L, 1L), new BackupRange(10L, 10L));
  }

  // --- extendRange ---

  @Test
  void shouldUpdateRangeEnd() {
    // given
    state.startNewRange(1L);

    // when
    state.updateRangeEnd(1L, 5L);

    // then
    final var ranges = state.getAllRanges();
    assertThat(ranges).containsExactly(new BackupRange(1L, 5L));
  }

  @Test
  void shouldUpdateRangeEndMultipleTimes() {
    // given
    state.startNewRange(1L);

    // when
    state.updateRangeEnd(1L, 3L);
    state.updateRangeEnd(1L, 7L);

    // then
    final var ranges = state.getAllRanges();
    assertThat(ranges).containsExactly(new BackupRange(1L, 7L));
  }

  // --- findRangeContaining ---

  @Test
  void shouldFindRangeContainingStartCheckpoint() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);

    // when
    final var result = state.findRangeContaining(1L);

    // then
    assertThat(result).isPresent().contains(new BackupRange(1L, 5L));
  }

  @Test
  void shouldFindRangeContainingEndCheckpoint() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);

    // when
    final var result = state.findRangeContaining(5L);

    // then
    assertThat(result).isPresent().contains(new BackupRange(1L, 5L));
  }

  @Test
  void shouldFindRangeContainingMiddleCheckpoint() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);

    // when
    final var result = state.findRangeContaining(3L);

    // then
    assertThat(result).isPresent().contains(new BackupRange(1L, 5L));
  }

  @Test
  void shouldReturnEmptyWhenCheckpointNotInAnyRange() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);

    // when
    final var result = state.findRangeContaining(6L);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenNoRangesExist() {
    // when
    final var result = state.findRangeContaining(1L);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldFindCorrectRangeAmongMultiple() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 3L);
    state.startNewRange(10L);
    state.updateRangeEnd(10L, 15L);

    // when/then — checkpoint in first range
    assertThat(state.findRangeContaining(2L)).isPresent().contains(new BackupRange(1L, 3L));

    // when/then — checkpoint in second range
    assertThat(state.findRangeContaining(12L)).isPresent().contains(new BackupRange(10L, 15L));

    // when/then — checkpoint between ranges
    assertThat(state.findRangeContaining(5L)).isEmpty();
  }

  @Test
  void shouldFindSingleCheckpointRange() {
    // given — a range with start == end
    state.startNewRange(5L);

    // when
    final var result = state.findRangeContaining(5L);

    // then
    assertThat(result).isPresent().contains(new BackupRange(5L, 5L));
  }

  // --- deleteRange ---

  @Test
  void shouldDeleteRange() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);

    // when
    state.deleteRange(1L);

    // then
    assertThat(state.getAllRanges()).isEmpty();
  }

  @Test
  void shouldDeleteOnlyTargetRange() {
    // given
    state.startNewRange(1L);
    state.startNewRange(10L);

    // when
    state.deleteRange(1L);

    // then
    assertThat(state.getAllRanges()).containsExactly(new BackupRange(10L, 10L));
  }

  // --- advanceRangeStart ---

  @Test
  void shouldUpdateRangeStart() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);

    // when
    state.updateRangeStart(1L, 2L);

    // then
    assertThat(state.getAllRanges()).containsExactly(new BackupRange(2L, 5L));
  }

  @Test
  void shouldNotAffectOtherRangesWhenAdvancing() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);
    state.startNewRange(10L);
    state.updateRangeEnd(10L, 15L);

    // when
    state.updateRangeStart(1L, 3L);

    // then
    assertThat(state.getAllRanges())
        .containsExactly(new BackupRange(3L, 5L), new BackupRange(10L, 15L));
  }

  // --- shrinkRangeEnd ---

  @Test
  void shouldShrinkRangeEnd() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 5L);

    // when
    state.updateRangeEnd(1L, 4L);

    // then
    assertThat(state.getAllRanges()).containsExactly(new BackupRange(1L, 4L));
  }

  // --- splitRange ---

  @Test
  void shouldSplitRangeIntoTwo() {
    // given — range [1, 10]
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 10L);

    // when — delete checkpoint 5, predecessor=4, successor=6
    state.splitRange(1L, 10L, 4L, 6L);

    // then — two sub-ranges: [1, 4] and [6, 10]
    assertThat(state.getAllRanges())
        .containsExactly(new BackupRange(1L, 4L), new BackupRange(6L, 10L));
  }

  @Test
  void shouldSplitRangeNearStart() {
    // given — range [1, 10]
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 10L);

    // when — delete checkpoint 2, predecessor=1, successor=3
    state.splitRange(1L, 10L, 1L, 3L);

    // then — [1, 1] and [3, 10]
    assertThat(state.getAllRanges())
        .containsExactly(new BackupRange(1L, 1L), new BackupRange(3L, 10L));
  }

  @Test
  void shouldSplitRangeNearEnd() {
    // given — range [1, 10]
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 10L);

    // when — delete checkpoint 9, predecessor=8, successor=10
    state.splitRange(1L, 10L, 8L, 10L);

    // then — [1, 8] and [10, 10]
    assertThat(state.getAllRanges())
        .containsExactly(new BackupRange(1L, 8L), new BackupRange(10L, 10L));
  }

  @Test
  void shouldNotAffectOtherRangesWhenSplitting() {
    // given
    state.startNewRange(1L);
    state.updateRangeEnd(1L, 10L);
    state.startNewRange(20L);
    state.updateRangeEnd(20L, 30L);

    // when
    state.splitRange(1L, 10L, 4L, 6L);

    // then
    assertThat(state.getAllRanges())
        .containsExactly(
            new BackupRange(1L, 4L), new BackupRange(6L, 10L), new BackupRange(20L, 30L));
  }

  // --- getAllRanges ---

  @Test
  void shouldReturnEmptyListWhenNoRanges() {
    // when/then
    assertThat(state.getAllRanges()).isEmpty();
  }

  @Test
  void shouldReturnRangesInOrder() {
    // given — insert out of order (ranges are still keyed by start ID)
    state.startNewRange(10L);
    state.startNewRange(1L);
    state.startNewRange(5L);

    // when
    final var ranges = state.getAllRanges();

    // then — ordered by start checkpoint ID
    assertThat(ranges)
        .containsExactly(
            new BackupRange(1L, 1L), new BackupRange(5L, 5L), new BackupRange(10L, 10L));
  }
}
