/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the element instance state contract against PostgreSQL, plus the tests for what is specific
 * to staging writes until commit.
 *
 * <p>The variable state is the RocksDB one of {@link ProcessingStateExtension}: the element
 * instance state only creates and removes scopes in it, and a real implementation lets the contract
 * verify those calls through {@link MutableVariableState#getParentScopeKey(long)}. It doesn't take
 * part in the JDBC transaction, so a rollback here doesn't undo its scopes; no test relies on that.
 *
 * <p>All tests share one database; each test writes to its own partition to stay isolated.
 *
 * <p>By default the database is a throwaway Testcontainers PostgreSQL. To inspect the rows after a
 * run, point the test at your own database with {@code
 * -Dzeebe.engine.rdbms.jdbcUrl=jdbc:postgresql:
 * //localhost:5432/postgres?user=postgres&password=zeebe}: the schema is created if missing and
 * committed rows are kept. Add {@code -Dzeebe.engine.rdbms.dumpLevel=debug} to log each test's rows
 * with the decoded payload (see {@link ElementInstanceTableDump}).
 */
@ExtendWith(ProcessingStateExtension.class)
final class RdbmsElementInstanceStateTest extends AbstractElementInstanceStateContractTest {

  private static final String SCHEMA_RESOURCE = "/db/engine-state/postgres/element_instance.sql";

  /**
   * Records every row-level write that reaches the table, so tests can tell how many statements a
   * commit sent.
   */
  private static final String WRITE_LOG_DDL =
      """
      CREATE TABLE element_instance_write_log (
        id BIGSERIAL PRIMARY KEY,
        partition_id INT NOT NULL,
        element_instance_key BIGINT NOT NULL,
        operation TEXT NOT NULL
      );
      CREATE FUNCTION log_element_instance_write() RETURNS TRIGGER AS $$
      BEGIN
        IF TG_OP = 'DELETE' THEN
          INSERT INTO element_instance_write_log
            (partition_id, element_instance_key, operation)
            VALUES (OLD.partition_id, OLD.element_instance_key, TG_OP);
        ELSE
          INSERT INTO element_instance_write_log
            (partition_id, element_instance_key, operation)
            VALUES (NEW.partition_id, NEW.element_instance_key, TG_OP);
        END IF;
        RETURN NULL;
      END;
      $$ LANGUAGE plpgsql;
      CREATE TRIGGER element_instance_write_log
        AFTER INSERT OR UPDATE OR DELETE ON element_instance
        FOR EACH ROW EXECUTE FUNCTION log_element_instance_write();
      """;

  private static final List<String> STATE_TABLES =
      List.of(
          "element_instance",
          "taken_sequence_flow",
          "await_process_instance_result",
          "runtime_instructions",
          "process_instance_by_business_id");

  private static final String EXTERNAL_JDBC_URL_PROPERTY = "zeebe.engine.rdbms.jdbcUrl";

  private static final AtomicInteger PARTITION_IDS = new AtomicInteger();
  private static @Nullable PostgreSQLContainer postgres;
  private static PGSimpleDataSource dataSource;

  private MutableProcessingState processingState;
  private Connection connection;
  private JdbcTransactionContext transactionContext;
  private int partitionId;

  @BeforeAll
  static void startDatabase() throws SQLException, IOException {
    dataSource = new PGSimpleDataSource();
    final var externalJdbcUrl = System.getProperty(EXTERNAL_JDBC_URL_PROPERTY);
    if (externalJdbcUrl != null && !externalJdbcUrl.isBlank()) {
      dataSource.setUrl(externalJdbcUrl);
    } else {
      postgres = new PostgreSQLContainer(DockerImageName.parse("postgres").withTag("15-alpine"));
      postgres.start();
      dataSource.setUrl(postgres.getJdbcUrl());
      dataSource.setUser(postgres.getUsername());
      dataSource.setPassword(postgres.getPassword());
    }

    try (final var schema =
            RdbmsElementInstanceStateTest.class.getResourceAsStream(SCHEMA_RESOURCE);
        final var setupConnection = dataSource.getConnection();
        final var statement = setupConnection.createStatement()) {
      // idempotent, so an external database that keeps its tables between runs gets new ones
      statement.execute(readSchema(schema));
      if (!tableExists(setupConnection, "element_instance_write_log")) {
        statement.execute(WRITE_LOG_DDL);
      }
      // continue after the partitions of earlier runs so their rows don't leak into these tests
      try (final var result =
          statement.executeQuery("SELECT COALESCE(MAX(partition_id), 0) FROM element_instance")) {
        result.next();
        PARTITION_IDS.set(result.getInt(1));
      }
    }
  }

  @AfterAll
  static void stopDatabase() {
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Override
  protected MutableElementInstanceState createElementInstanceState() {
    try {
      connection = dataSource.getConnection();
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
    transactionContext = new JdbcTransactionContext(connection);
    partitionId = PARTITION_IDS.incrementAndGet();
    return new RdbmsElementInstanceState(
        transactionContext, partitionId, processingState.getVariableState());
  }

  @Override
  protected MutableVariableState variableState() {
    return processingState.getVariableState();
  }

  @Override
  protected void assertNoStateLeft() {
    for (final String table : STATE_TABLES) {
      assertThat(countRows(table)).describedAs("Expected table %s to be empty", table).isZero();
    }
    // the variable scopes live in RocksDB
    ElementInstanceStateTest.assertNoColumnFamilyLeft(processingState);
  }

  @AfterEach
  void closeConnection() throws SQLException {
    if (connection != null) {
      connection.rollback();
      connection.close();
    }
    ElementInstanceTableDump.logRows(dataSource, partitionId);
  }

  @Test
  void shouldWriteRowsToDatabaseOnlyOnCommit() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    final var parent =
        elementInstanceState.newInstance(
            100,
            createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        parent, 101, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(committedRows()).isEmpty();

    // when
    transaction.commit();

    // then
    assertThat(committedRows())
        .containsExactly(
            new Row(
                100,
                -1,
                1000,
                2,
                (short) BpmnElementType.PROCESS.ordinal(),
                ProcessInstanceIntent.ELEMENT_ACTIVATED.value(),
                1),
            new Row(
                101,
                100,
                1000,
                2,
                (short) BpmnElementType.START_EVENT.ordinal(),
                ProcessInstanceIntent.ELEMENT_ACTIVATING.value(),
                0));
  }

  @Test
  void shouldDeleteRowOnCommitOfRemoval() throws Exception {
    // given
    createParentWithChildren(100, 101);

    // when
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.removeInstance(101);
    transaction.commit();

    // then
    assertThat(committedRows()).extracting(Row::key).containsExactly(100L);
    assertThat(committedRows().getFirst().childCount()).isZero();
  }

  @Test
  void shouldLeaveNothingOnRollback() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    createParentWithChildren(100, 101);

    // when
    transaction.rollback();

    // then
    assertThat(committedRows()).isEmpty();
    assertThat(elementInstanceState.getInstance(100)).isNull();
    assertThat(elementInstanceState.getChildren(100)).isEmpty();
  }

  @Test
  void shouldDiscardStagedRemovalOnRollback() throws Exception {
    // given
    createParentWithChildren(100, 101);
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.removeInstance(101);

    // when
    transaction.rollback();

    // then
    assertThat(elementInstanceState.getInstance(101)).isNotNull();
    assertThat(elementInstanceState.getInstance(100).getNumberOfActiveElementInstances())
        .isEqualTo(1);
  }

  @Test
  void shouldSeeOwnUncommittedWrites() {
    // given - child 101 committed, child 102 removed in the open transaction
    final var parent = createParentWithChildren(100, 101, 102);
    transactionContext.getCurrentTransaction();
    elementInstanceState.removeInstance(102);

    // when
    elementInstanceState.newInstance(
        elementInstanceState.getInstance(100),
        103,
        createProcessInstanceRecord(),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.updateInstance(101, child -> child.setJobKey(5));

    // then
    assertThat(committedRows()).extracting(Row::key).containsExactly(100L, 101L, 102L);
    assertThat(elementInstanceState.getInstance(102)).isNull();
    assertThat(elementInstanceState.getInstance(103)).isNotNull();
    assertThat(elementInstanceState.getInstance(101).getJobKey()).isEqualTo(5);
    assertThat(
            elementInstanceState.getInstance(parent.getKey()).getNumberOfActiveElementInstances())
        .isEqualTo(2);
    assertThat(elementInstanceState.getChildren(100))
        .extracting(ElementInstance::getKey)
        .containsExactly(101L, 103L);
    final List<Long> childKeys = new ArrayList<>();
    elementInstanceState.forEachChildKey(100, childKeys::add);
    assertThat(childKeys).containsExactly(101L, 103L);
  }

  @Test
  void shouldNotChangeStoredStateWhenMutatingReturnedInstance() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    final var created =
        elementInstanceState.newInstance(
            100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    created.setJobKey(5);
    elementInstanceState.getInstance(100).setJobKey(6);
    elementInstanceState.getChildren(100);
    transaction.commit();
    elementInstanceState.getInstance(100).setJobKey(7);

    // then
    assertThat(elementInstanceState.getInstance(100).getJobKey()).isZero();
    assertThat(committedRows().getFirst().jobKey()).isZero();
  }

  @Test
  void shouldCollapseRepeatedUpdatesIntoOneStatement() throws Exception {
    // given
    elementInstanceState.newInstance(
        100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var transaction = transactionContext.getCurrentTransaction();

    // when
    for (int jobKey = 1; jobKey <= 3; jobKey++) {
      final var instance = elementInstanceState.getInstance(100);
      instance.setJobKey(jobKey);
      elementInstanceState.updateInstance(instance);
    }
    transaction.commit();

    // then
    assertThat(writeLog()).containsExactly("INSERT 100", "UPDATE 100");
    assertThat(committedRows().getFirst().jobKey()).isEqualTo(3);
  }

  @Test
  void shouldWriteInsertedThenUpdatedInstanceAsOneInsert() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    final var instance =
        elementInstanceState.newInstance(
            100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.setJobKey(5);
    elementInstanceState.updateInstance(instance);
    transaction.commit();

    // then
    assertThat(writeLog()).containsExactly("INSERT 100");
    assertThat(committedRows().getFirst().jobKey()).isEqualTo(5);
  }

  @Test
  void shouldNotWriteInsertedThenRemovedInstance() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.newInstance(
        100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    elementInstanceState.removeInstance(100);
    transaction.commit();

    // then
    assertThat(writeLog()).isEmpty();
  }

  @Test
  void shouldWriteUpdatedThenRemovedInstanceAsOneDelete() throws Exception {
    // given
    elementInstanceState.newInstance(
        100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var transaction = transactionContext.getCurrentTransaction();

    // when
    elementInstanceState.updateInstance(100, instance -> instance.setJobKey(5));
    elementInstanceState.removeInstance(100);
    transaction.commit();

    // then
    assertThat(writeLog()).containsExactly("INSERT 100", "DELETE 100");
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldRejectUpdateOfMissingInstanceOnCommit() {
    // given
    final var neverCreated =
        new ElementInstance(
            100, ProcessInstanceIntent.ELEMENT_ACTIVATED, createProcessInstanceRecord());

    // when - no open transaction, so the update commits right away
    // then
    assertThatThrownBy(() -> elementInstanceState.updateInstance(neverCreated))
        .hasRootCauseMessage(
            "Expected to update element instance with key 100, but it does not exist");
    assertThat(committedRows()).isEmpty();
  }

  @Test
  void shouldSeeUncommittedProcessInstances() throws Exception {
    // given - process instance 100 committed, 101 removed and 102 created in the open transaction
    final var processInstance =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    elementInstanceState.newInstance(100, processInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(101, processInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var transaction = transactionContext.getCurrentTransaction();

    // when
    elementInstanceState.removeInstance(101);
    elementInstanceState.newInstance(102, processInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // then
    final long definitionKey = processInstance.getProcessDefinitionKey();
    assertThat(elementInstanceState.getProcessInstanceKeysByDefinitionKey(definitionKey))
        .containsExactly(100L, 102L);
    assertThat(elementInstanceState.getActiveProcessInstanceCount()).isEqualTo(2);
    assertThat(elementInstanceState.hasActiveProcessInstances(definitionKey, List.of(100L)))
        .isTrue();
    assertThat(elementInstanceState.hasActiveProcessInstances(definitionKey, List.of(100L, 102L)))
        .isFalse();

    // when
    transaction.rollback();

    // then
    assertThat(elementInstanceState.getProcessInstanceKeysByDefinitionKey(definitionKey))
        .containsExactly(100L, 101L);
    assertThat(elementInstanceState.getActiveProcessInstanceCount()).isEqualTo(2);
  }

  @Test
  void shouldUndoDirectWritesOnRollback() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        1, wrapString("gateway"), wrapString("flow"));
    elementInstanceState.insertProcessInstanceKeyByBusinessId("order-1", "process", "", 100L);
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(1)).isOne();

    // when
    transaction.rollback();

    // then
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(1)).isZero();
    assertThat(
            elementInstanceState.hasActiveProcessInstanceWithBusinessId(
                "order-1", "process", "", key -> false))
        .isFalse();
  }

  private List<Row> committedRows() {
    final List<Row> rows = new ArrayList<>();
    try (final var freshConnection = dataSource.getConnection();
        final var statement =
            freshConnection.prepareStatement(
                "SELECT element_instance_key, parent_key, process_instance_key,"
                    + " process_definition_key, bpmn_element_type, state, payload"
                    + " FROM element_instance WHERE partition_id = ?"
                    + " ORDER BY element_instance_key")) {
      statement.setInt(1, partitionId);
      try (final ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          final var payload = resultSet.getBytes(7);
          final var instance = new ElementInstance();
          instance.wrap(new UnsafeBuffer(payload), 0, payload.length);
          rows.add(
              new Row(
                  resultSet.getLong(1),
                  resultSet.getLong(2),
                  resultSet.getLong(3),
                  resultSet.getLong(4),
                  resultSet.getShort(5),
                  resultSet.getShort(6),
                  instance.getNumberOfActiveElementInstances(),
                  instance.getJobKey()));
        }
      }
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
    return rows;
  }

  private long countRows(final String table) {
    try (final var freshConnection = dataSource.getConnection();
        final var statement =
            freshConnection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE partition_id = ?")) {
      statement.setInt(1, partitionId);
      try (final ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<String> writeLog() throws SQLException {
    final List<String> writes = new ArrayList<>();
    try (final var freshConnection = dataSource.getConnection();
        final var statement =
            freshConnection.prepareStatement(
                "SELECT operation, element_instance_key FROM element_instance_write_log"
                    + " WHERE partition_id = ? ORDER BY id")) {
      statement.setInt(1, partitionId);
      try (final ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          writes.add(resultSet.getString(1) + " " + resultSet.getLong(2));
        }
      }
    }
    return writes;
  }

  private static boolean tableExists(final Connection connection, final String table)
      throws SQLException {
    try (final var statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
      statement.setString(1, table);
      try (final var result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      }
    }
  }

  private static String readSchema(final InputStream schema) throws IOException {
    if (schema == null) {
      throw new IllegalStateException("Missing schema resource " + SCHEMA_RESOURCE);
    }
    return new String(schema.readAllBytes(), StandardCharsets.UTF_8);
  }

  private record Row(
      long key,
      long parentKey,
      long processInstanceKey,
      long processDefinitionKey,
      short bpmnElementType,
      short state,
      int childCount,
      long jobKey) {

    Row(
        final long key,
        final long parentKey,
        final long processInstanceKey,
        final long processDefinitionKey,
        final short bpmnElementType,
        final short state,
        final int childCount) {
      this(
          key,
          parentKey,
          processInstanceKey,
          processDefinitionKey,
          bpmnElementType,
          state,
          childCount,
          0);
    }
  }
}
