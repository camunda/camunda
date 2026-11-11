/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionParticipant;
import io.camunda.zeebe.db.impl.jdbc.MixedTransactionContext;
import io.camunda.zeebe.db.impl.jdbc.MixedZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.util.DefaultZeebeDbFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * With a mixed RocksDB/RDBMS state, the processed position must also reach the relational database,
 * which keeps its changes across restarts and decides with it whether to apply a replayed
 * transaction.
 */
final class DbLastProcessedPositionStateTest {

  @TempDir private File tempFolder;
  private ZeebeDb<ZbColumnFamilies> rocksDb;
  private MixedTransactionContext mixedContext;
  private final List<String> participantCalls = new ArrayList<>();

  @BeforeEach
  void createDb() {
    rocksDb = DefaultZeebeDbFactory.defaultFactory().createDb(tempFolder);
  }

  @AfterEach
  void closeDb() throws Exception {
    rocksDb.close();
  }

  @Test
  void shouldWriteThePositionToTheRelationalDatabase() throws Exception {
    // given - a database without a processed position
    final var state = mixedState(-1);

    // when
    final long position = markAsProcessedAndCommit(state, 7);

    // then
    assertThat(participantCalls).containsExactly("marked 7", "commit", "reset");
    assertThat(position).isEqualTo(7);
  }

  @Test
  void shouldDiscardTheRelationalChangesOfAPositionTheDatabaseContains() throws Exception {
    // given - a database that contains the changes of position 7
    final var state = mixedState(7);

    // when
    final long position = markAsProcessedAndCommit(state, 7);

    // then - only RocksDB applies it
    assertThat(participantCalls).containsExactly("marked 7", "rollback", "reset");
    assertThat(position).isEqualTo(7);
  }

  private DbLastProcessedPositionState mixedState(final long committedPosition) {
    final var mixedDb = new MixedZeebeDb<>(rocksDb, committedPosition);
    mixedContext = (MixedTransactionContext) mixedDb.createContext();
    mixedContext.jdbc().register(new RecordingParticipant(participantCalls));
    return new DbLastProcessedPositionState(mixedDb, mixedContext);
  }

  /** Commits like the stream processor, and returns the position RocksDB committed. */
  private long markAsProcessedAndCommit(
      final DbLastProcessedPositionState state, final long position) throws Exception {
    final var transaction = mixedContext.getCurrentTransaction();
    transaction.run(() -> state.markAsProcessed(position));
    transaction.commit();
    return new DbLastProcessedPositionState(rocksDb, rocksDb.createContext())
        .getLastSuccessfulProcessedRecordPosition();
  }

  private record RecordingParticipant(List<String> calls) implements JdbcTransactionParticipant {

    @Override
    public void commit() {
      calls.add("commit");
    }

    @Override
    public void rollback() {
      calls.add("rollback");
    }

    @Override
    public void commitFailed() {
      calls.add("failed");
    }

    @Override
    public void markAsProcessed(final long position) {
      calls.add("marked " + position);
    }

    @Override
    public void reset() {
      calls.add("reset");
    }
  }
}
