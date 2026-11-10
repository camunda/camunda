/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Counts what a commit sends to PostgreSQL. Each statement the batches execute is a round trip, so
 * the commit of a transaction that touches several tables must send one batch per distinct
 * statement: all rows of a table written by the same statement share a batch, and the tables are
 * written through a single MyBatis session.
 */
@ExtendWith(ProcessingStateExtension.class)
final class EngineStateRoundTripsTest {

  private static final String TENANT_ID = "round-trip-tenant";

  private static EngineStateDatabase database;

  private MutableProcessingState processingState;
  private CountingConnection counting;
  private CountingSessionFactory sessions;
  private JdbcTransactionContext transactionContext;
  private MutableElementInstanceState elementInstanceState;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @BeforeEach
  void setUp() throws SQLException {
    counting = new CountingConnection(database.dataSource().getConnection());
    transactionContext = new JdbcTransactionContext(counting.proxy());
    sessions = new CountingSessionFactory(database.sessionFactory());
    elementInstanceState =
        new ElementInstanceStateImpl(
            new RdbmsElementInstanceStore(
                EngineStateDatabase.entitySession(
                    transactionContext, database.nextPartitionId(), sessions.factory())),
            processingState.getVariableState());
  }

  @AfterEach
  void closeConnection() throws SQLException {
    counting.delegate.rollback();
    counting.delegate.close();
  }

  @Test
  void shouldWriteAllTablesWithOneBatchPerStatementInOneSession() throws Exception {
    // given - a new instance with a parent, a taken sequence flow and an awaiting client
    final var transaction = transactionContext.getCurrentTransaction();
    final var parent =
        elementInstanceState.newInstance(
            100,
            record(BpmnElementType.PROCESS, "process"),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        parent,
        101,
        record(BpmnElementType.EXCLUSIVE_GATEWAY, "gateway"),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    elementInstanceState.setAwaitResultRequestMetadata(
        100, TENANT_ID, new AwaitProcessInstanceResultMetadata().setRequestId(1));
    counting.reset();

    // when
    transaction.commit();

    // then - the parent's update collapsed into its insert, which shares a batch with the child's
    assertThat(counting.batches)
        .containsExactlyInAnyOrder(
            new Batch("CAM_RU_ELEMENT_INSTANCE", "INSERT", 2),
            new Batch("CAM_RU_TAKEN_SEQUENCE_FLOW", "INSERT", 1),
            new Batch("CAM_RU_AWAIT_PROCESS_INSTANCE_RESULT", "INSERT", 1));
    assertThat(counting.otherStatements).isZero();
    assertThat(sessions.opened()).isOne();
    assertThat(counting.commits).isOne();
    System.out.println("ROUND TRIPS commit 1: " + counting);
  }

  @Test
  void shouldBatchDeletesInsertsAndUpdatesOfATableSeparately() throws Exception {
    // given - committed parent, child and taken sequence flow
    final var setup = transactionContext.getCurrentTransaction();
    final var parent =
        elementInstanceState.newInstance(
            100,
            record(BpmnElementType.PROCESS, "process"),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        parent,
        101,
        record(BpmnElementType.EXCLUSIVE_GATEWAY, "gateway"),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    setup.commit();
    sessions.reset();

    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.updateInstance(101, child -> child.setJobKey(5));
    elementInstanceState.newInstance(
        elementInstanceState.getInstance(100),
        102,
        record(BpmnElementType.SERVICE_TASK, "task"),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.newInstance(
        elementInstanceState.getInstance(100),
        103,
        record(BpmnElementType.SERVICE_TASK, "task"),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.decrementNumberOfTakenSequenceFlows(100, wrapString("gateway"));
    counting.reset();

    // when
    transaction.commit();

    // then
    assertThat(counting.batches)
        .containsExactlyInAnyOrder(
            new Batch("CAM_RU_ELEMENT_INSTANCE", "INSERT", 2),
            new Batch("CAM_RU_ELEMENT_INSTANCE", "UPDATE", 2),
            new Batch("CAM_RU_TAKEN_SEQUENCE_FLOW", "DELETE", 1));
    assertThat(counting.otherStatements).isZero();
    assertThat(sessions.opened()).isOne();
    assertThat(counting.commits).isOne();
  }

  @Test
  void shouldSendNothingWhenNothingIsStaged() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.getInstance(100);
    counting.reset();

    // when
    transaction.commit();

    // then
    assertThat(counting.batches).isEmpty();
    assertThat(sessions.opened()).isOne();
    assertThat(counting.commits).isOne();
  }

  @Test
  void shouldOpenOneSessionForAllReadsAndTheFlushOfATransaction() throws Exception {
    // given - a transaction that reads several times before it writes
    final var first = transactionContext.getCurrentTransaction();
    elementInstanceState.getInstance(100);
    elementInstanceState.getInstance(101);
    elementInstanceState.newInstance(
        100, record(BpmnElementType.PROCESS, "process"), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.getInstance(100);
    assertThat(sessions.opened()).isOne();

    // when
    first.commit();

    // then - the flush used the same session
    assertThat(sessions.opened()).isOne();

    // when - a second transaction
    final var second = transactionContext.getCurrentTransaction();
    elementInstanceState.getInstance(100);
    elementInstanceState.getInstance(101);
    second.commit();

    // then - opens its own
    assertThat(sessions.opened()).isEqualTo(2);
  }

  private static ProcessInstanceRecord record(final BpmnElementType type, final String elementId) {
    return new ProcessInstanceRecord()
        .setElementId(elementId)
        .setBpmnProcessId(wrapString("process"))
        .setProcessInstanceKey(100)
        .setFlowScopeKey(100)
        .setVersion(1)
        .setProcessDefinitionKey(2)
        .setTenantId(TENANT_ID)
        .setBpmnElementType(type);
  }

  private record Batch(String table, String operation, int rows) {}

  /** Counts the batches a commit executes, and anything else that talks to the database. */
  private static final class CountingConnection implements InvocationHandler {

    private static final Set<String> EXECUTE_METHODS =
        Set.of("execute", "executeUpdate", "executeQuery", "executeLargeUpdate");

    private final Connection delegate;
    private final List<Batch> batches = new ArrayList<>();
    private int otherStatements;
    private int commits;

    private CountingConnection(final Connection delegate) {
      this.delegate = delegate;
    }

    private Connection proxy() {
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, this);
    }

    private void reset() {
      batches.clear();
      otherStatements = 0;
      commits = 0;
    }

    @Override
    public String toString() {
      return "executeBatch=%d %s, commits=%d".formatted(batches.size(), batches, commits);
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
        throws Throwable {
      switch (method.getName()) {
        case "commit" -> commits++;
        default -> {}
      }
      final Object result = call(delegate, method, args);
      if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement) {
        return countingStatement((String) args[0], (PreparedStatement) result);
      }
      return result;
    }

    private PreparedStatement countingStatement(final String sql, final PreparedStatement target) {
      final int[] rows = {0};
      return (PreparedStatement)
          Proxy.newProxyInstance(
              PreparedStatement.class.getClassLoader(),
              new Class<?>[] {PreparedStatement.class},
              (statement, method, args) -> {
                final var name = method.getName();
                if (name.equals("addBatch")) {
                  rows[0]++;
                } else if (name.equals("executeBatch")) {
                  batches.add(batch(sql, rows[0]));
                  rows[0] = 0;
                } else if (EXECUTE_METHODS.contains(name)) {
                  otherStatements++;
                }
                return call(target, method, args);
              });
    }

    private static Batch batch(final String sql, final int rows) {
      final var words = sql.trim().split("\\s+");
      final var operation = words[0].toUpperCase();
      final var table =
          switch (operation) {
            case "INSERT" -> words[2];
            case "UPDATE" -> words[1];
            case "DELETE" -> words[2];
            default -> sql;
          };
      return new Batch(table, operation, rows);
    }

    private static Object call(final Object target, final Method method, final Object[] args)
        throws Throwable {
      try {
        return method.invoke(target, args);
      } catch (final InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }
}
