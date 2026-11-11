/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import io.camunda.zeebe.protocol.Protocol;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the session on the partition state table of PostgreSQL, whose rows have a key and a value;
 * the first character of the value stands for a group that queries select. The connection logs
 * every batch the session sends.
 */
final class EntitySessionTest {

  private static final EntityType<String, PartitionStateRow> ROW =
      new EntityType<>(
          PartitionStateMapper.class, "PartitionState", "test row", PartitionStateRow::key);

  private static EngineStateDatabase database;

  private final List<String> batches = new ArrayList<>();
  private final List<String> connectionCalls = new ArrayList<>();
  private int selects;
  private boolean failNextBatch;
  private int partitionId;
  private JdbcTransactionContext context;
  private EntitySession session;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @BeforeEach
  void setUp() {
    database.observed().reset();
    database.observed().decorate(this::loggingProxy);
    partitionId = database.nextPartitionId();
    context = new JdbcTransactionContext();
    session = database.entitySession(context, partitionId);
  }

  @AfterEach
  void tearDown() throws Exception {
    context.getCurrentTransaction().rollback();
    database.observed().reset();
  }

  @Test
  void shouldWriteOnlyOnCommit() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    session.insert(ROW, row("1", "a"));

    // when - then
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "a"));
    assertThat(committedRows()).isEmpty();
    transaction.commit();
    assertThat(committedRows()).containsExactly(row("1", "a"));
    assertThat(session.stagedKeys(ROW)).isEmpty();
  }

  @Test
  void shouldCollapseInsertAndUpdateIntoInsert() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();

    // when
    session.insert(ROW, row("1", "a"));
    session.update(ROW, row("1", "b"));
    transaction.commit();

    // then
    assertThat(batches).containsExactly("INSERT 1");
    assertThat(committedRows()).containsExactly(row("1", "b"));
  }

  @Test
  void shouldNotWriteInsertedThenDeletedRow() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();

    // when
    session.insert(ROW, row("1", "a"));
    session.delete(ROW, "1");
    transaction.commit();

    // then
    assertThat(batches).isEmpty();
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldReadWithoutDatabaseTransactionInReadOnlySession() throws Exception {
    // given
    commit(row("1", "a"));
    final var readOnlyContext = new JdbcTransactionContext();
    final var readOnly =
        new EntitySession(readOnlyContext, partitionId, database.sessionFactory(), true);
    readOnlyContext.register(readOnly);
    connectionCalls.clear();

    // when
    final var transaction = readOnlyContext.getCurrentTransaction();
    final var read = readOnly.get(ROW, "1");
    transaction.commit();

    // then - the connection stays in auto-commit mode, so nothing begins or ends a transaction
    assertThat(read).isEqualTo(row("1", "a"));
    assertThat(connectionCalls).containsExactly("close");
    assertThatThrownBy(() -> readOnly.insert(ROW, row("2", "a")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldNotDeleteRowKnownToBeMissing() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    session.get(ROW, "1");
    session.get(ROW, "2");

    // when
    session.delete(ROW, "1");
    session.delete(ROW, "2");
    transaction.commit();

    // then - only the existing row is deleted
    assertThat(batches).containsExactly("DELETE 1");
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldCollapseDeleteAndInsertIntoUpdate() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();

    // when
    session.delete(ROW, "1");
    assertThat(session.get(ROW, "1")).isNull();
    session.insert(ROW, row("1", "b"));
    transaction.commit();

    // then
    assertThat(batches).containsExactly("UPDATE 1");
    assertThat(committedRows()).containsExactly(row("1", "b"));
  }

  @Test
  void shouldCollapseUpdateAndDeleteIntoDelete() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();

    // when
    session.update(ROW, row("1", "b"));
    session.delete(ROW, "1");
    transaction.commit();

    // then
    assertThat(batches).containsExactly("DELETE 1");
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldBatchDeletesInsertsAndUpdatesSeparately() throws Exception {
    // given
    commit(row("1", "a"), row("2", "b"), row("3", "c"));
    final var transaction = context.getCurrentTransaction();

    // when
    session.update(ROW, row("1", "a2"));
    session.insert(ROW, row("4", "d"));
    session.delete(ROW, "2");
    session.insert(ROW, row("5", "e"));
    session.update(ROW, row("3", "c2"));
    session.delete(ROW, "3");
    transaction.commit();

    // then
    assertThat(batches).containsExactly("DELETE 2", "INSERT 2", "UPDATE 1");
    assertThat(committedRows()).containsExactly(row("1", "a2"), row("4", "d"), row("5", "e"));
  }

  @Test
  void shouldUpsertUnknownRowsByDeleteAndInsert() throws Exception {
    // given - one committed row, one missing; the session read neither
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    selects = 0;

    // when
    session.upsert(ROW, row("1", "b"));
    session.upsert(ROW, row("2", "c"));
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "b"));
    transaction.commit();

    // then - no read, both overwritten through one delete and one insert batch
    assertThat(selects).isZero();
    assertThat(batches).containsExactly("DELETE 2", "INSERT 2");
    assertThat(committedRows()).containsExactly(row("1", "b"), row("2", "c"));
  }

  @Test
  void shouldUpsertKnownRowsAsUpdateOrInsert() throws Exception {
    // given - the session knows that 1 exists and 2 doesn't
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    session.get(ROW, "1");
    session.get(ROW, "2");

    // when
    session.upsert(ROW, row("1", "b"));
    session.upsert(ROW, row("2", "c"));
    session.insert(ROW, row("3", "d"));
    session.upsert(ROW, row("3", "e"));
    transaction.commit();

    // then
    assertThat(batches).containsExactly("INSERT 2", "UPDATE 1");
    assertThat(committedRows()).containsExactly(row("1", "b"), row("2", "c"), row("3", "e"));
  }

  @Test
  void shouldDeleteUpsertedRow() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    session.upsert(ROW, row("1", "b"));

    // when
    session.delete(ROW, "1");
    transaction.commit();

    // then
    assertThat(session.get(ROW, "1")).isNull();
    assertThat(batches).containsExactly("DELETE 1");
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldRejectInvalidTransitions() {
    // given
    context.getCurrentTransaction();
    session.insert(ROW, row("1", "a"));
    session.delete(ROW, "2");

    // when - then
    assertThatThrownBy(() -> session.insert(ROW, row("1", "b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected to insert test row with key 1, but it already exists");
    assertThatThrownBy(() -> session.update(ROW, row("2", "b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Expected to update test row with key 2, but it was removed");
  }

  @Test
  void shouldFailCommitOnUpdateOfMissingRow() {
    // given
    final var transaction = context.getCurrentTransaction();
    session.update(ROW, row("1", "a"));

    // when - then
    assertThatThrownBy(transaction::commit)
        .hasRootCauseMessage("Expected to update test row with key 1, but it does not exist");
  }

  @Test
  void shouldMergeQueryResultsWithStagedWrites() throws Exception {
    // given - committed 1, 2, 3 in group a
    commit(row("1", "a"), row("2", "a"), row("3", "a"));
    context.getCurrentTransaction();

    // when - 1 moves to group b, 2 is deleted, 3 is changed, 4 is added
    session.update(ROW, row("1", "b"));
    session.delete(ROW, "2");
    session.update(ROW, row("3", "a3"));
    session.insert(ROW, row("4", "a4"));

    // then
    final var dbResults = committedRows();
    assertThat(dbResults).extracting(PartitionStateRow::key).containsExactly("1", "2", "3");
    assertThat(
            session.query(
                ROW, dbResults, inGroup('a'), Comparator.comparing(PartitionStateRow::key)))
        .containsExactly(row("3", "a3"), row("4", "a4"));
    assertThat(
            session.queryKeys(
                ROW,
                dbResults.stream().map(PartitionStateRow::key).toList(),
                inGroup('a'),
                Comparator.reverseOrder()))
        .containsExactly("4", "3");
    assertThat(session.stagedMatching(ROW, inGroup('b'))).containsExactly(row("1", "b"));
    assertThat(session.stagedKeys(ROW)).containsExactlyInAnyOrder("1", "2", "3", "4");
  }

  @Test
  void shouldFlushSameWritesAgainWhenCommitIsRetried() throws Exception {
    // given - the first flush fails after the rows were written
    commit(row("1", "a"), row("2", "b"));
    final var transaction = context.getCurrentTransaction();
    session.insert(ROW, row("3", "c"));
    session.update(ROW, row("1", "a2"));
    session.delete(ROW, "2");
    failNextBatch = true;

    // when
    assertThatThrownBy(transaction::commit).isInstanceOf(ZeebeDbException.class);
    assertThat(committedRows()).extracting(PartitionStateRow::key).containsExactly("1", "2");
    transaction.commit();

    // then
    assertThat(committedRows()).containsExactly(row("1", "a2"), row("3", "c"));
  }

  @Test
  void shouldReadUnchangedRowOnceAndMissingRowOncePerTransaction() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    selects = 0;

    // when
    final var first = session.get(ROW, "1");
    final var second = session.get(ROW, "1");
    final var missing = session.get(ROW, "2");
    final var missingAgain = session.get(ROW, "2");

    // then
    assertThat(first).isEqualTo(row("1", "a")).isSameAs(second);
    assertThat(missing).isNull();
    assertThat(missingAgain).isNull();
    assertThat(selects).isEqualTo(2);

    // when - a new transaction
    transaction.commit();
    context.getCurrentTransaction();
    session.get(ROW, "1");

    // then
    assertThat(selects).isEqualTo(3);
  }

  @Test
  void shouldNotReadRowsOfKeysGeneratedInTransaction() {
    // given
    final long firstGeneratedKey = Protocol.encodePartitionId(partitionId, 100);
    context.getCurrentTransaction();
    selects = 0;

    // when
    session.keyGenerated(firstGeneratedKey);
    session.keyGenerated(firstGeneratedKey + 1);

    // then - rows of the generated keys can't exist yet
    assertThat(session.get(RdbmsProcessStore.PROCESS, firstGeneratedKey)).isNull();
    assertThat(session.get(RdbmsProcessStore.PROCESS, firstGeneratedKey + 5)).isNull();
    assertThat(selects).isZero();

    // then - older keys and keys of other partitions are read
    session.get(RdbmsProcessStore.PROCESS, firstGeneratedKey - 1);
    session.get(RdbmsProcessStore.PROCESS, Protocol.encodePartitionId(partitionId + 1, 1));
    assertThat(selects).isEqualTo(2);
  }

  @Test
  void shouldReadKeysGeneratedInPreviousTransaction() throws Exception {
    // given
    final long generatedKey = Protocol.encodePartitionId(partitionId, 100);
    final var transaction = context.getCurrentTransaction();
    session.keyGenerated(generatedKey);
    transaction.commit();
    context.getCurrentTransaction();
    selects = 0;

    // when
    session.get(RdbmsProcessStore.PROCESS, generatedKey);

    // then
    assertThat(selects).isOne();
  }

  @Test
  void shouldServeRowsOfFullRowQueryWithoutSelect() throws Exception {
    // given
    commit(row("1", "a"), row("2", "a"), row("3", "a"));
    context.getCurrentTransaction();
    session.update(ROW, row("3", "a3"));

    // when - 3 is staged, so the query result of it must not replace it
    session.query(ROW, committedRows(), inGroup('a'), Comparator.comparing(PartitionStateRow::key));
    selects = 0;

    // then
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "a"));
    assertThat(session.get(ROW, "2")).isEqualTo(row("2", "a"));
    assertThat(session.get(ROW, "3")).isEqualTo(row("3", "a3"));
    assertThat(selects).isZero();
  }

  @Test
  void shouldNotReplaceLoadedRowWithQueryResult() throws Exception {
    // given
    commit(row("1", "a"));
    context.getCurrentTransaction();
    final var loaded = session.get(ROW, "1");

    // when
    session.query(
        ROW, List.of(row("1", "a")), inGroup('a'), Comparator.comparing(PartitionStateRow::key));

    // then
    assertThat(session.get(ROW, "1")).isSameAs(loaded);
  }

  @Test
  void shouldPreferStagedWritesOverLoadedRows() throws Exception {
    // given - 1 and 2 exist, 3 doesn't; all loaded
    commit(row("1", "a"), row("2", "b"));
    context.getCurrentTransaction();
    session.get(ROW, "1");
    session.get(ROW, "2");
    session.get(ROW, "3");

    // when
    session.update(ROW, row("1", "a2"));
    session.delete(ROW, "2");
    session.insert(ROW, row("3", "c"));

    // then
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "a2"));
    assertThat(session.get(ROW, "2")).isNull();
    assertThat(session.get(ROW, "3")).isEqualTo(row("3", "c"));

    // when - the staged insert is deleted again
    session.delete(ROW, "3");

    // then - the row is absent as loaded
    assertThat(session.get(ROW, "3")).isNull();
  }

  @Test
  void shouldReadAgainAfterRollback() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    session.get(ROW, "1");
    selects = 0;

    // when
    transaction.rollback();
    session.get(ROW, "1");

    // then
    assertThat(selects).isOne();
  }

  @Test
  void shouldReadAgainAfterFailedCommit() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    session.get(ROW, "1");
    session.insert(ROW, row("2", "b"));
    failNextBatch = true;
    assertThatThrownBy(transaction::commit).isInstanceOf(ZeebeDbException.class);
    selects = 0;

    // when
    final var row = session.get(ROW, "1");
    session.get(ROW, "1");

    // then
    assertThat(row).isEqualTo(row("1", "a"));
    assertThat(selects).isOne();
    assertThat(session.get(ROW, "2")).isEqualTo(row("2", "b"));
  }

  @Test
  void shouldDropStagedWritesOnRollback() throws Exception {
    // given
    commit(row("1", "a"));
    final var transaction = context.getCurrentTransaction();
    session.delete(ROW, "1");
    session.insert(ROW, row("2", "b"));

    // when
    transaction.rollback();

    // then
    assertThat(session.stagedKeys(ROW)).isEmpty();
    assertThat(session.get(ROW, "1")).isEqualTo(row("1", "a"));
    assertThat(session.get(ROW, "2")).isNull();
  }

  @Test
  void shouldRetryCommitOnNewConnectionWhenConnectionBroke() throws Exception {
    // given - a transaction that read through a session on its connection
    final var connections = new ArrayList<Connection>();
    database
        .observed()
        .decorate(
            connection -> {
              connections.add(connection);
              return loggingProxy(connection);
            });
    final var sessions = new CountingSessionFactory(database.sessionFactory());
    final var brokenContext = new JdbcTransactionContext();
    final var brokenSession =
        EngineStateDatabase.entitySession(brokenContext, partitionId, sessions.factory());
    final var transaction = brokenContext.getCurrentTransaction();
    brokenSession.insert(ROW, row("1", "a"));
    assertThat(brokenSession.get(ROW, "2")).isNull();
    assertThat(sessions.opened()).isOne();
    connections.getFirst().close();

    // when - the commit fails because the connection is gone
    assertThatThrownBy(transaction::commit).isInstanceOf(ZeebeDbException.class);

    // then - its session is closed and the connection returned, the writes are still staged
    assertThat(sessions.closed()).isOne();
    assertThat(database.observed().returned()).isOne();
    assertThat(brokenSession.stagedKeys(ROW)).containsExactly("1");

    // when - the retry
    transaction.commit();

    // then - it flushed through a new session on a new connection
    assertThat(connections).hasSize(2);
    assertThat(sessions.opened()).isEqualTo(2);
    assertThat(sessions.closed()).isEqualTo(2);
    assertThat(committedRows()).containsExactly(row("1", "a"));

    // when - later reads
    final var next = brokenContext.getCurrentTransaction();
    final var read = brokenSession.get(ROW, "1");
    next.commit();

    // then
    assertThat(read).isEqualTo(row("1", "a"));
    assertThat(connections).hasSize(3);
    assertThat(database.observed().borrowed()).isEqualTo(3);
    assertThat(database.observed().returned()).isEqualTo(3);
  }

  @Test
  void shouldBorrowAndReturnOneConnectionPerTransaction() throws Exception {
    // when - a transaction that reads and writes
    var transaction = context.getCurrentTransaction();
    session.insert(ROW, row("1", "a"));
    session.get(ROW, "2");
    session.get(ROW, "3");
    assertThat(database.observed().borrowed()).isOne();
    assertThat(database.observed().returned()).isZero();
    transaction.commit();

    // then
    assertThat(database.observed().borrowed()).isOne();
    assertThat(database.observed().returned()).isOne();

    // when - a read-only transaction
    transaction = context.getCurrentTransaction();
    session.get(ROW, "1");
    transaction.commit();

    // then
    assertThat(database.observed().borrowed()).isEqualTo(2);
    assertThat(database.observed().returned()).isEqualTo(2);

    // when - a transaction that only stages writes
    transaction = context.getCurrentTransaction();
    session.update(ROW, row("1", "b"));
    assertThat(database.observed().borrowed()).isEqualTo(2);
    transaction.commit();

    // then
    assertThat(database.observed().borrowed()).isEqualTo(3);
    assertThat(database.observed().returned()).isEqualTo(3);

    // when - a rolled back transaction
    transaction = context.getCurrentTransaction();
    session.get(ROW, "1");
    transaction.rollback();

    // then
    assertThat(database.observed().borrowed()).isEqualTo(4);
    assertThat(database.observed().returned()).isEqualTo(4);
  }

  @Test
  void shouldNotBorrowConnectionInTransactionThatDoesNotTouchTheDatabase() throws Exception {
    // when
    context.getCurrentTransaction().commit();
    context.getCurrentTransaction().rollback();
    context.runInTransaction(() -> {});

    // then
    assertThat(database.observed().borrowed()).isZero();
    assertThat(database.observed().returned()).isZero();
  }

  @Test
  void shouldCommitThroughTheSessionAndReturnTheConnection() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    session.insert(ROW, row("1", "a"));
    session.get(ROW, "2");
    assertThat(connectionCalls).doesNotContain("commit", "rollback", "close");

    // when
    transaction.commit();

    // then
    assertThat(connectionCalls).containsOnlyOnce("commit", "close").doesNotContain("rollback");
    assertThat(committedRows()).containsExactly(row("1", "a"));
  }

  @Test
  void shouldCommitReadOnlyTransactionWithoutWriting() throws Exception {
    // given
    commit(row("1", "a"));
    connectionCalls.clear();
    final var transaction = context.getCurrentTransaction();
    session.get(ROW, "1");

    // when
    transaction.commit();

    // then
    assertThat(batches).isEmpty();
    assertThat(connectionCalls).containsOnlyOnce("commit", "close").doesNotContain("rollback");
  }

  @Test
  void shouldRollbackThroughTheSessionAndReturnTheConnection() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    session.insert(ROW, row("1", "a"));
    session.get(ROW, "2");

    // when
    transaction.rollback();

    // then
    assertThat(connectionCalls).containsOnlyOnce("rollback", "close").doesNotContain("commit");
    assertThat(batches).isEmpty();
    assertThat(committedRows()).isEmpty();
  }

  private static PartitionStateRow row(final String key, final String value) {
    return new PartitionStateRow(key, value);
  }

  private static Predicate<PartitionStateRow> inGroup(final char group) {
    return row -> row.value() != null && row.value().charAt(0) == group;
  }

  private void commit(final PartitionStateRow... rows) throws Exception {
    final var transaction = context.getCurrentTransaction();
    for (final var row : rows) {
      session.insert(ROW, row);
    }
    transaction.commit();
    batches.clear();
  }

  /** The committed rows of the test's partition, as another connection sees them. */
  private List<PartitionStateRow> committedRows() throws SQLException {
    final List<PartitionStateRow> rows = new ArrayList<>();
    try (final var observer = database.dataSource().getConnection();
        final var statement =
            observer.prepareStatement(
                "SELECT state_key, value FROM CAM_RU_PARTITION_STATE"
                    + " WHERE partition_id = ? ORDER BY state_key")) {
      statement.setInt(1, partitionId);
      try (final var result = statement.executeQuery()) {
        while (result.next()) {
          rows.add(row(result.getString(1), result.getString(2)));
        }
      }
    }
    return rows;
  }

  /**
   * Logs every executed batch as its operation and number of rows, and fails the next batch after
   * executing it if asked to.
   */
  private Connection loggingProxy(final Connection target) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (Set.of("commit", "rollback", "close").contains(method.getName())) {
                connectionCalls.add(method.getName());
              } else if (method.getName().equals("setAutoCommit")) {
                connectionCalls.add("setAutoCommit(" + args[0] + ")");
              }
              final Object result = invoke(target, method, args);
              if (method.getName().equals("prepareStatement")
                  && result instanceof final PreparedStatement statement) {
                return loggingStatement((String) args[0], statement);
              }
              return result;
            });
  }

  private PreparedStatement loggingStatement(final String sql, final PreparedStatement target) {
    final var operation = sql.trim().split("\\s+")[0].toUpperCase();
    final int[] rows = {0};
    return (PreparedStatement)
        Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
              final Object result = invoke(target, method, args);
              if (method.getName().startsWith("execute")
                  && !method.getName().equals("executeBatch")) {
                selects++;
              }
              if (method.getName().equals("addBatch")) {
                rows[0]++;
              } else if (method.getName().equals("executeBatch")) {
                batches.add(operation + " " + rows[0]);
                rows[0] = 0;
                if (failNextBatch) {
                  failNextBatch = false;
                  throw new SQLTransientException("injected failure after writing");
                }
              }
              return result;
            });
  }

  private static Object invoke(final Object target, final Method method, final Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (final InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
