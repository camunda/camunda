/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.ZeebeDbException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class JdbcTransactionContextTest {

  private String url;
  private Connection connection;
  private Connection observer;
  private JdbcTransactionContext context;
  private StagingParticipant participant;

  @BeforeEach
  void setUp() throws SQLException {
    url = "jdbc:h2:mem:" + UUID.randomUUID();
    connection = DriverManager.getConnection(url);
    connection.setAutoCommit(false);
    observer = DriverManager.getConnection(url);
    try (final var statement = observer.createStatement()) {
      statement.execute("CREATE TABLE test_rows (id BIGINT PRIMARY KEY, val VARCHAR(255))");
    }
    context = new JdbcTransactionContext();
    participant = new StagingParticipant(connection);
    context.register(participant);
  }

  @AfterEach
  void tearDown() throws SQLException {
    connection.close();
    observer.close();
  }

  @Test
  void shouldFlushStagedWritesOnCommit() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");
    participant.stage(2, "b");

    // when
    transaction.commit();

    // then
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "a", 2L, "b"));
    assertThat(participant.flushes).isOne();
    assertThat(participant.resets).isOne();
    assertThat(participant.staged).isEmpty();
  }

  @Test
  void shouldDiscardStagedWritesOnRollback() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");

    // when
    transaction.rollback();

    // then
    assertThat(visibleRows()).isEmpty();
    assertThat(participant.flushes).isZero();
    assertThat(participant.resets).isOne();
    assertThat(participant.staged).isEmpty();
  }

  @Test
  void shouldCommitCallThatIsNotInATransaction() throws Exception {
    // given
    participant.stage(1, "a");

    // when
    final int result = context.call(() -> 42);

    // then
    assertThat(result).isEqualTo(42);
    assertThat(participant.flushes).isOne();
    assertThat(visibleRows()).containsExactlyEntriesOf(Map.of(1L, "a"));
  }

  @Test
  void shouldJoinCurrentTransactionInCall() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");

    // when
    context.call(() -> null);

    // then
    assertThat(participant.flushes).isZero();
    transaction.commit();
    assertThat(participant.flushes).isOne();
  }

  @Test
  void shouldTranslateSqlExceptionOfCall() {
    // when - then
    assertThatThrownBy(
            () ->
                context.call(
                    () -> {
                      throw new SQLTransientException("busy");
                    }))
        .isInstanceOf(ZeebeDbException.class);
    assertThatThrownBy(
            () ->
                context.call(
                    () -> {
                      throw new SQLException("duplicate key", "23505");
                    }))
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(ZeebeDbException.class);
  }

  @Test
  void shouldNotifyListenersOfCommit() throws Exception {
    // given
    final var outcomes = new ArrayList<String>();
    context.addTransactionListener(recording(outcomes));
    final var transaction = context.getCurrentTransaction();

    // when
    transaction.commit();

    // then
    assertThat(outcomes).containsExactly("commit");
  }

  @Test
  void shouldNotifyListenersOnlyOfRollbackWhenCommitFailed() throws Exception {
    // given
    final var outcomes = new ArrayList<String>();
    context.addTransactionListener(recording(outcomes));
    final var transaction = context.getCurrentTransaction();
    participant.failNextFlushWith = new SQLException("duplicate key", "23505");
    assertThatThrownBy(transaction::commit).isInstanceOf(RuntimeException.class);

    // when
    transaction.rollback();

    // then
    assertThat(outcomes).containsExactly("rollback");
  }

  @Test
  void shouldNotPersistRolledBackWritesOnNextCommit() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");
    transaction.rollback();

    // when
    final var next = context.getCurrentTransaction();
    participant.stage(2, "b");
    next.commit();

    // then
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(2L, "b"));
  }

  @Test
  void shouldCommitAtEndOfRunInTransaction() throws Exception {
    // when
    context.runInTransaction(() -> participant.stage(1, "a"));

    // then
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "a"));
    assertThat(participant.resets).isOne();
  }

  @Test
  void shouldCommitNestedRunInTransactionOnlyWhenOuterCompletes() throws Exception {
    // given
    final Map<Long, String> visibleAfterInner = new LinkedHashMap<>();

    // when
    context.runInTransaction(
        () -> {
          participant.stage(1, "outer");
          context.runInTransaction(() -> participant.stage(2, "inner"));
          visibleAfterInner.putAll(visibleRows());
        });

    // then
    assertThat(visibleAfterInner).isEmpty();
    assertThat(participant.flushes).isOne();
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "outer", 2L, "inner"));
  }

  @Test
  void shouldRollbackAndRethrowRuntimeExceptionInRunInTransaction() throws Exception {
    // given
    final var failure = new IllegalStateException("expected");

    // when
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      participant.stage(1, "a");
                      throw failure;
                    }))
        // then
        .isSameAs(failure);
    assertThat(visibleRows()).isEmpty();
    assertThat(participant.flushes).isZero();
    assertThat(participant.resets).isOne();
    assertThat(participant.staged).isEmpty();
  }

  @Test
  void shouldRollbackAndWrapCheckedExceptionInRunInTransaction() throws Exception {
    // given
    final var failure = new Exception("expected");

    // when
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      participant.stage(1, "a");
                      throw failure;
                    }))
        // then
        .isInstanceOf(RuntimeException.class)
        .hasCause(failure);
    assertThat(visibleRows()).isEmpty();
    assertThat(participant.resets).isOne();
  }

  @Test
  void shouldStartNewTransactionAfterRunInTransactionFailed() throws Exception {
    // given
    assertThatThrownBy(
        () ->
            context.runInTransaction(
                () -> {
                  participant.stage(1, "a");
                  throw new IllegalStateException("expected");
                }));

    // when
    context.runInTransaction(() -> participant.stage(2, "b"));

    // then
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(2L, "b"));
  }

  @Test
  void shouldKeepStagedWritesWhenCommitFailsOnConstraintViolation() throws Exception {
    // given
    insertFromObserver(1, "conflict");
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");
    participant.stage(2, "b");

    // when
    assertThatThrownBy(transaction::commit)
        // then
        .isExactlyInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(SQLException.class);
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "conflict"));
    assertThat(participant.resets).isZero();
    assertThat(participant.staged).hasSize(2);

    // when
    deleteFromObserver(1);
    transaction.commit();

    // then
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "a", 2L, "b"));
    assertThat(participant.resets).isOne();
  }

  @Test
  void shouldPersistWritesExactlyOnceWhenCommitIsRetriedAfterTransientFailure() throws Exception {
    // given
    participant.failNextFlushWith = new SQLTransientException("expected transient failure");
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");
    participant.stage(2, "b");

    // when
    assertThatThrownBy(transaction::commit)
        // then
        .isInstanceOf(ZeebeDbException.class)
        .hasCauseInstanceOf(SQLTransientException.class);
    assertThat(visibleRows()).isEmpty();
    assertThat(participant.resets).isZero();
    assertThat(participant.failedCommits).isOne();

    // when
    transaction.commit();

    // then
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "a", 2L, "b"));
    assertThat(participant.flushes).isEqualTo(2);
    assertThat(participant.resets).isOne();
  }

  @Test
  void shouldCommitWritesStagedThroughCurrentTransactionRun() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();

    // when
    transaction.run(() -> participant.stage(1, "a"));
    transaction.run(() -> participant.stage(2, "b"));
    final var visibleBeforeCommit = visibleRows();
    transaction.commit();

    // then
    assertThat(visibleBeforeCommit).isEmpty();
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "a", 2L, "b"));
  }

  @Test
  void shouldJoinCurrentTransactionFromRunInTransaction() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();

    // when
    transaction.run(() -> context.runInTransaction(() -> participant.stage(1, "a")));
    final var visibleBeforeCommit = visibleRows();
    transaction.commit();

    // then
    assertThat(visibleBeforeCommit).isEmpty();
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "a"));
  }

  @ParameterizedTest
  @MethodSource("recoverableDriverErrors")
  void shouldTreatDriverErrorsWithRetryableSqlStateAsRecoverable(final SQLException error)
      throws Exception {
    // given
    participant.failNextFlushWith = error;
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");

    // when
    assertThatThrownBy(transaction::commit)
        // then
        .isInstanceOf(ZeebeDbException.class)
        .hasCause(error);

    // when
    transaction.commit();

    // then
    assertThat(visibleRows()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "a"));
  }

  @Test
  void shouldNotTreatDriverErrorWithOtherSqlStateAsRecoverable() {
    // given - unique violation, as PostgreSQL reports it
    participant.failNextFlushWith = new SQLException("duplicate key", "23505");
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");

    // when
    assertThatThrownBy(transaction::commit)
        // then
        .isExactlyInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(SQLException.class);
  }

  @Test
  void shouldRollbackParticipantsOnRollback() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    participant.stage(1, "a");

    // when
    transaction.rollback();

    // then
    assertThat(participant.rollbacks).isOne();
    assertThat(participant.commits).isZero();
  }

  @Test
  void shouldCommitParticipantsInRegistrationOrder() throws Exception {
    // given
    final var order = new ArrayList<String>();
    final var first = new RecordingParticipant("first", order);
    final var second = new RecordingParticipant("second", order);
    final var ordered = new JdbcTransactionContext();
    ordered.register(first);
    ordered.register(second);

    // when
    ordered.getCurrentTransaction().commit();

    // then
    assertThat(order)
        .containsExactly("first commit", "second commit", "first reset", "second reset");
  }

  @Test
  void shouldTellParticipantsThatCommitFailedAndKeepTheirChanges() throws Exception {
    // given
    final var order = new ArrayList<String>();
    final var failing = new RecordingParticipant("failing", order);
    failing.failCommitWith = new SQLException("connection lost", "08006");
    final var other = new RecordingParticipant("other", order);
    final var failingContext = new JdbcTransactionContext();
    failingContext.register(failing);
    failingContext.register(other);

    // when
    assertThatThrownBy(() -> failingContext.getCurrentTransaction().commit())
        // then
        .isInstanceOf(ZeebeDbException.class);
    assertThat(order).containsExactly("failing commit", "failing failed", "other failed");
  }

  /** A participant that only records the calls it gets. */
  private static final class RecordingParticipant implements JdbcTransactionParticipant {

    private final String name;
    private final List<String> calls;
    private @Nullable SQLException failCommitWith;

    private RecordingParticipant(final String name, final List<String> calls) {
      this.name = name;
      this.calls = calls;
    }

    @Override
    public void commit() throws SQLException {
      calls.add(name + " commit");
      if (failCommitWith != null) {
        throw failCommitWith;
      }
    }

    @Override
    public void rollback() {
      calls.add(name + " rollback");
    }

    @Override
    public void commitFailed() {
      calls.add(name + " failed");
    }

    @Override
    public void reset() {
      calls.add(name + " reset");
    }
  }

  /**
   * PostgreSQL reports retryable failures as plain SQLExceptions identified only by SQLState, and
   * batch execution wraps them in a BatchUpdateException.
   */
  private static Stream<Arguments> recoverableDriverErrors() {
    final var batchFailure = new BatchUpdateException("batch failed", "40001", new int[0]);
    batchFailure.setNextException(new SQLException("could not serialize access", "40001"));
    final var wrappedBatchFailure = new BatchUpdateException("batch failed", new int[0]);
    wrappedBatchFailure.setNextException(new SQLException("deadlock detected", "40P01"));
    return Stream.of(
        Arguments.of(Named.of("deadlock", new SQLException("deadlock detected", "40P01"))),
        Arguments.of(
            Named.of("serialization failure", new SQLException("could not serialize", "40001"))),
        Arguments.of(
            Named.of("connection failure", new SQLException("connection refused", "08006"))),
        Arguments.of(Named.of("batch with retryable SQLState", batchFailure)),
        Arguments.of(Named.of("batch wrapping retryable error", wrappedBatchFailure)));
  }

  private Map<Long, String> visibleRows() throws SQLException {
    return rows(observer);
  }

  private static Map<Long, String> rows(final Connection connection) throws SQLException {
    final Map<Long, String> rows = new LinkedHashMap<>();
    try (final var statement = connection.createStatement();
        final var result = statement.executeQuery("SELECT id, val FROM test_rows ORDER BY id")) {
      while (result.next()) {
        rows.put(result.getLong(1), result.getString(2));
      }
    }
    return rows;
  }

  private void insertFromObserver(final long id, final String value) throws SQLException {
    try (final var statement =
        observer.prepareStatement("INSERT INTO test_rows (id, val) VALUES (?, ?)")) {
      statement.setLong(1, id);
      statement.setString(2, value);
      statement.executeUpdate();
    }
  }

  private void deleteFromObserver(final long id) throws SQLException {
    try (final var statement = observer.prepareStatement("DELETE FROM test_rows WHERE id = ?")) {
      statement.setLong(1, id);
      statement.executeUpdate();
    }
  }

  /** Stages rows in memory and inserts them with one JDBC batch on flush. */
  private static TransactionListener recording(final List<String> outcomes) {
    return new TransactionListener() {
      @Override
      public void onCommit() {
        outcomes.add("commit");
      }

      @Override
      public void onRollback() {
        outcomes.add("rollback");
      }
    };
  }

  private static final class StagingParticipant implements JdbcTransactionParticipant {

    private final Connection connection;
    private final Map<Long, String> staged = new LinkedHashMap<>();
    private int flushes;
    private int commits;
    private int rollbacks;
    private int resets;
    private int failedCommits;
    private @Nullable SQLException failNextFlushWith;

    private StagingParticipant(final Connection connection) {
      this.connection = connection;
    }

    void stage(final long id, final String value) {
      staged.put(id, value);
    }

    /** Inserts the staged rows with one JDBC batch, then commits, like the entity session does. */
    @Override
    public void commit() throws SQLException {
      flushes++;
      try (final var statement =
          connection.prepareStatement("INSERT INTO test_rows (id, val) VALUES (?, ?)")) {
        for (final var row : staged.entrySet()) {
          statement.setLong(1, row.getKey());
          statement.setString(2, row.getValue());
          statement.addBatch();
        }
        statement.executeBatch();
      }
      if (failNextFlushWith != null) {
        final var failure = failNextFlushWith;
        failNextFlushWith = null;
        throw failure;
      }
      connection.commit();
      commits++;
    }

    @Override
    public void rollback() {
      rollbacks++;
      rollbackQuietly();
    }

    @Override
    public void commitFailed() {
      failedCommits++;
      rollbackQuietly();
    }

    @Override
    public void reset() {
      resets++;
      staged.clear();
    }

    private void rollbackQuietly() {
      try {
        connection.rollback();
      } catch (final SQLException ignored) {
        // nothing to roll back on a broken connection
      }
    }
  }
}
