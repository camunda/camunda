/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DbPositionSupplierTest {

  @TempDir Path directory;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private DbCheckpointState checkpointState;
  private DbLogArchiveState logArchiveState;

  @BeforeEach
  void setUp() {
    zeebeDb = DefaultZeebeDbFactory.<ZbColumnFamilies>defaultFactory().createDb(directory.toFile());
    final var context = zeebeDb.createContext();
    checkpointState = new DbCheckpointState(zeebeDb, context);
    logArchiveState = new DbLogArchiveState(zeebeDb, context);
  }

  @AfterEach
  void tearDown() throws Exception {
    zeebeDb.close();
  }

  @Test
  void shouldNotLimitCompactionWhenBothAreDisabled() {
    // given
    setBackupPosition(10);
    setArchivedPosition(5);
    final var supplier = new DbPositionSupplier(zeebeDb, false, false);

    // when
    final long position = supplier.getHighestBackupPosition();

    // then
    assertThat(position).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void shouldUseBackupPositionWhenOnlyContinuousBackupIsEnabled() {
    // given
    setBackupPosition(10);
    setArchivedPosition(5);
    final var supplier = new DbPositionSupplier(zeebeDb, true, false);

    // when
    final long position = supplier.getHighestBackupPosition();

    // then
    assertThat(position).isEqualTo(10);
  }

  @Test
  void shouldUseArchivedPositionWhenOnlyLogArchiveIsEnabled() {
    // given
    setBackupPosition(10);
    setArchivedPosition(5);
    final var supplier = new DbPositionSupplier(zeebeDb, false, true);

    // when
    final long position = supplier.getHighestBackupPosition();

    // then
    assertThat(position).isEqualTo(5);
  }

  @Test
  void shouldUseLowerPositionWhenBothAreEnabled() {
    // given
    setBackupPosition(10);
    setArchivedPosition(15);
    final var supplier = new DbPositionSupplier(zeebeDb, true, true);

    // when
    final long position = supplier.getHighestBackupPosition();

    // then
    assertThat(position).isEqualTo(10);
  }

  @Test
  void shouldReturnMinusOneWhileNothingIsArchived() {
    // given - no timeline started yet, so there is no meta row
    final var supplier = new DbPositionSupplier(zeebeDb, false, true);

    // when
    final long position = supplier.getHighestBackupPosition();

    // then - -1 makes the state controller keep the previous snapshot's index
    assertThat(position).isEqualTo(-1);
  }

  @Test
  void shouldReturnMinusOneWhileNoApplicationEntryIsArchived() {
    // given
    setArchivedPosition(-1);
    final var supplier = new DbPositionSupplier(zeebeDb, false, true);

    // when
    final long position = supplier.getHighestBackupPosition();

    // then
    assertThat(position).isEqualTo(-1);
  }

  @Test
  void shouldFollowArchivedPositionAsItAdvances() {
    // given
    setArchivedPosition(5);
    final var supplier = new DbPositionSupplier(zeebeDb, false, true);
    assertThat(supplier.getHighestBackupPosition()).isEqualTo(5);

    // when
    setArchivedPosition(42);

    // then
    assertThat(supplier.getHighestBackupPosition()).isEqualTo(42);
  }

  private void setBackupPosition(final long position) {
    checkpointState.setLatestBackupInfo(1, position, 1, CheckpointType.MANUAL_BACKUP, 1);
  }

  private void setArchivedPosition(final long position) {
    logArchiveState.setMeta(
        new LogArchiveMeta()
            .setTimelineId("tl-0")
            .setStartIndex(1)
            .setArchivedIndex(position < 0 ? 0 : 3)
            .setArchivedPosition(position));
  }
}
