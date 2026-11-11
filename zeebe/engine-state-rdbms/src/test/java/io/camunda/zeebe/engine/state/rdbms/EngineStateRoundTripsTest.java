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
import io.camunda.zeebe.engine.state.immutable.JobState.ActivatableJobVisitor;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.EventScopeInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.JobStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.variable.VariableStateImpl;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Counts what a commit sends to PostgreSQL. Each statement the batches execute is a round trip, so
 * the commit of a transaction that touches several tables must send one batch per distinct
 * statement: all rows of a table written by the same statement share a batch, and the tables are
 * written through a single MyBatis session.
 */
final class EngineStateRoundTripsTest {

  private static final String TENANT_ID = "round-trip-tenant";

  private static EngineStateDatabase database;

  private CountingConnection counting;
  private CountingSessionFactory sessions;
  private JdbcTransactionContext transactionContext;
  private MutableElementInstanceState elementInstanceState;
  private MutableVariableState variableState;
  private MutableJobState jobState;
  private MutableEventScopeInstanceState eventScopeState;

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
    counting = new CountingConnection();
    database.observed().reset();
    database.observed().decorate(counting::wrap);
    transactionContext = new JdbcTransactionContext();
    sessions = new CountingSessionFactory(database.sessionFactory());
    final var session =
        EngineStateDatabase.entitySession(
            transactionContext, database.nextPartitionId(), sessions.factory());
    variableState = new VariableStateImpl(new RdbmsVariableStore(session));
    elementInstanceState =
        new ElementInstanceStateImpl(new RdbmsElementInstanceStore(session), variableState);
    jobState = new JobStateImpl(new RdbmsJobStore(session));
    eventScopeState = new EventScopeInstanceStateImpl(new RdbmsEventScopeStore(session));
  }

  @AfterEach
  void endTransaction() throws Exception {
    transactionContext.getCurrentTransaction().rollback();
    database.observed().reset();
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

  @Test
  void shouldReadEachUnchangedRowOncePerCommand() throws Exception {
    // given - committed parent and child
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
    setup.commit();

    // when - a command that reads the scopes repeatedly, then removes the child
    final var transaction = transactionContext.getCurrentTransaction();
    counting.reset();
    elementInstanceState.getInstance(100);
    elementInstanceState.getInstance(101);
    elementInstanceState.getInstance(100);
    elementInstanceState.getInstance(101);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    elementInstanceState.removeInstance(101);

    // then - one SELECT per distinct row or table read: 10 without the loaded-row cache. The
    // removal reads the child's variables to delete them.
    final int selects = counting.otherStatements;
    transaction.rollback();
    assertThat(selects).isEqualTo(5);
  }

  @Test
  void shouldActivateAndCompleteATaskWithItsEventScope() throws Exception {
    // given - a committed process instance
    final var setup = transactionContext.getCurrentTransaction();
    elementInstanceState.newInstance(
        100, record(BpmnElementType.PROCESS, "process"), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    setup.commit();

    // when - the task is activated like the activating applier does it: the element instance
    // first, then the event scope; the processor then looks for a trigger of the task
    final var activation = transactionContext.getCurrentTransaction();
    counting.reset();
    elementInstanceState.newInstance(
        elementInstanceState.getInstance(100),
        101,
        record(BpmnElementType.SERVICE_TASK, "task"),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
    eventScopeState.createInstance(101, List.of(), List.of());
    assertThat(eventScopeState.peekEventTrigger(101)).isNull();
    final int activationSelects = counting.otherStatements;
    activation.commit();
    final var activationBatches = List.copyOf(counting.batches);

    // when - a later command completes the task: the output mappings peek for a trigger, the
    // completed applier deletes the event scope and removes the element instance
    final var completion = transactionContext.getCurrentTransaction();
    counting.reset();
    elementInstanceState.getInstance(101);
    assertThat(eventScopeState.peekEventTrigger(101)).isNull();
    assertThat(eventScopeState.peekEventTrigger(101)).isNull();
    eventScopeState.deleteInstance(101);
    elementInstanceState.removeInstance(101);
    final int completionSelects = counting.otherStatements;
    completion.commit();

    // then - the activation reads the process instance (the parent of the task) and the triggers
    // of the task. The completion reads the task, its triggers once (which tells that there
    // are none, so the deletion of the event scope reads nothing), and for the removal of the
    // element instance its variables, its taken sequence flows and its parent
    System.out.printf(
        "ROUND TRIPS task activation: %d selects, %s; completion: %d selects, %s%n",
        activationSelects, activationBatches, completionSelects, counting);
    assertThat(activationSelects).isEqualTo(2);
    assertThat(activationBatches)
        .containsExactlyInAnyOrder(
            new Batch("CAM_RU_ELEMENT_INSTANCE", "INSERT", 1),
            new Batch("CAM_RU_ELEMENT_INSTANCE", "UPDATE", 1),
            new Batch("CAM_RU_EVENT_SCOPE", "INSERT", 1));
    assertThat(completionSelects).isEqualTo(5);
    assertThat(counting.batches)
        .containsExactlyInAnyOrder(
            new Batch("CAM_RU_ELEMENT_INSTANCE", "DELETE", 1),
            new Batch("CAM_RU_ELEMENT_INSTANCE", "UPDATE", 1),
            new Batch("CAM_RU_AWAIT_PROCESS_INSTANCE_RESULT", "DELETE", 1),
            new Batch("CAM_RU_EVENT_SCOPE", "DELETE", 1));
  }

  @ParameterizedTest(name = "{0} jobs, requested variables ''{1}''")
  @CsvSource({"1, ''", "10, ''", "1, a b", "10, a b"})
  void shouldActivateJobsWithTheirVariablesInThreeQueries(
      final int jobCount, final String requestedVariables) throws Exception {
    // given - committed jobs of tasks in a process instance, with variables in both scopes
    final var setup = transactionContext.getCurrentTransaction();
    final var processInstance =
        elementInstanceState.newInstance(
            100,
            record(BpmnElementType.PROCESS, "process")
                .setElementInstancePath(List.of(List.of(100L))),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    variableState.setVariableLocal(1, 100, 2, wrapString("a"), MsgPackUtil.asMsgPack("1"));
    for (int i = 0; i < jobCount; i++) {
      final long taskKey = 101 + i;
      elementInstanceState.newInstance(
          processInstance,
          taskKey,
          record(BpmnElementType.SERVICE_TASK, "task")
              .setElementInstancePath(List.of(List.of(100L, taskKey))),
          ProcessInstanceIntent.ELEMENT_ACTIVATED);
      variableState.setVariableLocal(
          2 + i, taskKey, 2, wrapString("b"), MsgPackUtil.asMsgPack(String.valueOf(i)));
      final var job =
          new JobRecord()
              .setType("task")
              .setTenantId(TENANT_ID)
              .setProcessInstanceKey(100)
              .setElementInstanceKey(taskKey);
      jobState.insertJobRecordActivatable(1000 + i, job);
      jobState.makeJobActivatableByPriority(job.getTypeBuffer(), 1000 + i, TENANT_ID, 0);
    }
    setup.commit();

    // when - activating them like the job batch collector: prefetch the variables of the jobs
    // read ahead, then read the document of each job
    final List<DirectBuffer> names =
        requestedVariables.isBlank()
            ? List.of()
            : Arrays.stream(requestedVariables.split(" ")).map(BufferUtil::wrapString).toList();
    final var transaction = transactionContext.getCurrentTransaction();
    counting.reset();
    final Map<Long, String> documents = new LinkedHashMap<>();
    jobState.forEachActivatableJobs(
        wrapString("task"),
        List.of(TENANT_ID),
        jobCount,
        new ActivatableJobVisitor() {
          @Override
          public Boolean apply(final Long key, final JobRecord job) {
            final long scopeKey = job.getElementInstanceKey();
            documents.put(
                key,
                MsgPackConverter.convertToJson(
                    names.isEmpty()
                        ? variableState.getVariablesAsDocument(scopeKey)
                        : variableState.getVariablesAsDocument(scopeKey, names)));
            return documents.size() < jobCount;
          }

          @Override
          public void readAhead(final long[] jobKeys) {
            final List<Long> scopeKeys = new ArrayList<>();
            for (final long jobKey : jobKeys) {
              scopeKeys.add(jobState.getJob(jobKey).getElementInstanceKey());
            }
            variableState.prefetchVariables(scopeKeys, names);
          }
        });
    final int selects = counting.otherStatements;
    for (final long jobKey : documents.keySet()) {
      final var job = new JobRecord();
      job.wrap(jobState.getJob(jobKey));
      jobState.activate(jobKey, job.setDeadline(5000).setWorker("worker"));
    }
    final int selectsWithActivation = counting.otherStatements;
    transaction.commit();

    // then - the job page, the element instances of the jobs and the variables of their chains,
    // whatever the number of jobs
    assertThat(documents).hasSize(jobCount);
    assertThat(documents.get(1000L)).isEqualTo("{\"b\":0,\"a\":1}");
    assertThat(selects).isEqualTo(3);
    assertThat(selectsWithActivation).isEqualTo(3);
    assertThat(counting.batches).containsExactly(new Batch("CAM_RU_JOB", "UPDATE", jobCount));
    System.out.printf(
        "ROUND TRIPS activation of %d jobs (variables '%s'): %d selects, %s%n",
        jobCount, requestedVariables, selects, counting);
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
  private static final class CountingConnection {

    private static final Set<String> EXECUTE_METHODS =
        Set.of("execute", "executeUpdate", "executeQuery", "executeLargeUpdate");

    private final List<Batch> batches = new ArrayList<>();
    private int otherStatements;
    private int commits;

    private Connection wrap(final Connection delegate) {
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> invoke(delegate, method, args));
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

    private Object invoke(final Connection delegate, final Method method, final Object[] args)
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
