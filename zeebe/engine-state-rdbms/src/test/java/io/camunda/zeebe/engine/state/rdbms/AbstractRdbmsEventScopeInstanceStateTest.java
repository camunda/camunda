/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.AbstractEventScopeInstanceStateContractTest;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.EventScopeInstance;
import io.camunda.zeebe.engine.state.instance.EventScopeInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.rdbms.ProcessDefinitionMapper.ProcessDefinitionRow;
import io.camunda.zeebe.engine.state.variable.VariableStateImpl;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the event scope state contract against PostgreSQL, plus the tests for what is specific to
 * the relational store: the rows and their process instance columns, rollback, the order of the
 * triggers across committed and staged ones, and how many queries the trigger reads send.
 *
 * <p>Every event scope of the engine is an element instance, which the activating applier creates
 * in the same transaction, and every start event belongs to a process definition. The contract
 * tests use bare keys, so they run against a state that creates these first, see {@link
 * WithElementInstancesAndProcesses}.
 *
 * <p>All tests share one {@link EngineStateDatabase}; each test writes to its own partition.
 */
final class RdbmsEventScopeInstanceStateTest extends AbstractEventScopeInstanceStateContractTest {

  private static final String TENANT_ID = "event-scope-tenant";
  private static final long PROCESS_INSTANCE_KEY = 100;
  private static final long ROOT_PROCESS_INSTANCE_KEY = 50;

  private static EngineStateDatabase database;

  private final QueryCounter queryCounter = new QueryCounter();
  private JdbcTransactionContext transactionContext;
  private int partitionId;
  private EntitySession session;
  private MutableElementInstanceState elementInstanceState;
  private RdbmsEventScopeStore store;
  private MutableEventScopeInstanceState scopes;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Override
  protected MutableEventScopeInstanceState createEventScopeInstanceState() {
    database.observed().reset();
    database.observed().decorate(queryCounter::wrap);
    transactionContext = new JdbcTransactionContext();
    partitionId = database.nextPartitionId();
    session = database.entitySession(transactionContext, partitionId);
    elementInstanceState =
        new ElementInstanceStateImpl(
            new RdbmsElementInstanceStore(session),
            new VariableStateImpl(new RdbmsVariableStore(session)));
    store = new RdbmsEventScopeStore(session);
    scopes = new EventScopeInstanceStateImpl(store);
    return new WithElementInstancesAndProcesses(scopes);
  }

  @AfterEach
  void endTransaction() throws Exception {
    transactionContext.getCurrentTransaction().rollback();
    database.observed().reset();
  }

  @Test
  void shouldWriteRowsWithProcessInstanceOfElementInstanceOnCommit() throws Exception {
    // given - like the activating applier: the element instance first, then its scope
    final var transaction = transactionContext.getCurrentTransaction();
    newElementInstance(101);
    scopes.createInstance(101, Set.of(wrapString("boundary")), Set.of(wrapString("boundary")));
    scopes.triggerEvent(
        101, 1, wrapString("boundary"), wrapString("variables"), PROCESS_INSTANCE_KEY);
    assertThat(rows("SELECT event_scope_key FROM CAM_RU_EVENT_SCOPE")).isEmpty();

    // when
    transaction.commit();

    // then - the interrupting boundary event made the scope stop accepting
    assertThat(
            rows(
                "SELECT event_scope_key, accepting, interrupted, process_instance_key,"
                    + " root_process_instance_key, tenant_id FROM CAM_RU_EVENT_SCOPE"))
        .containsExactly(
            tuple(101L, false, true, PROCESS_INSTANCE_KEY, ROOT_PROCESS_INSTANCE_KEY, TENANT_ID));
    assertThat(
            rows(
                "SELECT event_scope_key, event_key, element_id, variables, process_instance_key,"
                    + " root_process_instance_key, tenant_id FROM CAM_RU_EVENT_TRIGGER"))
        .containsExactly(
            tuple(
                101L,
                1L,
                "boundary",
                "variables".getBytes(),
                PROCESS_INSTANCE_KEY,
                ROOT_PROCESS_INSTANCE_KEY,
                TENANT_ID));
    final var instance = scopes.getInstance(101);
    assertThat(instance.isInterruptingElementId(wrapString("boundary"))).isTrue();
    assertThat(instance.isBoundaryElementId(wrapString("boundary"))).isTrue();
  }

  @Test
  void shouldTakeProcessInstanceOfCommittedScopeForItsTriggers() throws Exception {
    // given
    createCommittedScope(101);

    // when
    final var transaction = transactionContext.getCurrentTransaction();
    final var queries =
        queryCounter.queriesOf(
            () ->
                scopes.triggerEvent(
                    101, 1, wrapString("event"), wrapString(""), PROCESS_INSTANCE_KEY));
    transaction.commit();

    // then - the scope was read to check that it accepts the event, its element instance wasn't
    assertThat(queries).containsExactly("CAM_RU_EVENT_SCOPE");
    assertThat(
            rows(
                "SELECT event_key, variables, process_instance_key, root_process_instance_key,"
                    + " tenant_id FROM CAM_RU_EVENT_TRIGGER"))
        .containsExactly(
            tuple(1L, new byte[0], PROCESS_INSTANCE_KEY, ROOT_PROCESS_INSTANCE_KEY, TENANT_ID));
    assertThat(scopes.peekEventTrigger(101).getVariables().capacity()).isZero();
  }

  @Test
  void shouldFailToCreateAScopeWithoutElementInstance() {
    // given
    transactionContext.getCurrentTransaction();

    // when - then
    assertThatThrownBy(() -> scopes.createInstance(101, Set.of(), Set.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected event scope 101 to belong to an element instance, but it does not exist");
  }

  @Test
  void shouldLeaveNothingOnRollback() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    newElementInstance(101);
    scopes.createInstance(101, Set.of(), Set.of());
    scopes.triggerEvent(101, 1, wrapString("event"), wrapString(""), PROCESS_INSTANCE_KEY);
    assertThat(scopes.peekEventTrigger(101).getEventKey()).isOne();

    // when
    transaction.rollback();

    // then
    assertThat(scopes.getInstance(101)).isNull();
    assertThat(scopes.peekEventTrigger(101)).isNull();
    assertThat(rows("SELECT event_scope_key FROM CAM_RU_EVENT_SCOPE")).isEmpty();
    assertThat(rows("SELECT event_key FROM CAM_RU_EVENT_TRIGGER")).isEmpty();
  }

  @Test
  void shouldVisitCommittedAndStagedTriggersInEventKeyOrder() throws Exception {
    // given - committed triggers 2, 4, 6 and 8, more than the first page holds
    createCommittedScope(101);
    final var setup = transactionContext.getCurrentTransaction();
    for (final long eventKey : List.of(8L, 2L, 6L, 4L)) {
      trigger(101, eventKey);
    }
    setup.commit();

    // when - the transaction triggers 1, 5 and 9 and takes 4
    final var transaction = transactionContext.getCurrentTransaction();
    for (final long eventKey : List.of(9L, 5L, 1L)) {
      trigger(101, eventKey);
    }
    scopes.deleteTrigger(101, 4);

    // then
    assertThat(visitedEventKeys(101)).containsExactly(1L, 2L, 5L, 6L, 8L, 9L);
    assertThat(scopes.peekEventTrigger(101).getEventKey()).isOne();
    transaction.commit();
    assertThat(rows("SELECT event_key FROM CAM_RU_EVENT_TRIGGER"))
        .containsExactly(tuple(1L), tuple(2L), tuple(5L), tuple(6L), tuple(8L), tuple(9L));
  }

  @Test
  void shouldReadTheTriggersOfAScopeWithOneQuery() throws Exception {
    // given
    createCommittedScope(101);
    final var setup = transactionContext.getCurrentTransaction();
    for (long eventKey = 1; eventKey <= 10; eventKey++) {
      trigger(101, eventKey);
    }
    setup.commit();
    transactionContext.getCurrentTransaction();

    // when - then
    final List<Long> visited = new ArrayList<>();
    assertThat(queryCounter.queriesOf(() -> visitedEventKeys(101, visited)))
        .containsExactly("CAM_RU_EVENT_TRIGGER");
    assertThat(visited).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

    // all known now
    assertThat(queryCounter.queriesOf(() -> scopes.peekEventTrigger(101))).isEmpty();
    assertThat(queryCounter.queriesOf(() -> scopes.deleteInstance(101))).isEmpty();
  }

  @Test
  void shouldPollTheTriggerWithTheLowestEventKey() throws Exception {
    // given - committed 3 and 5, staged 4
    createCommittedScope(101);
    final var setup = transactionContext.getCurrentTransaction();
    trigger(101, 5);
    trigger(101, 3);
    setup.commit();
    final var transaction = transactionContext.getCurrentTransaction();
    trigger(101, 4);

    // when - then
    assertThat(scopes.pollEventTrigger(101).getEventKey()).isEqualTo(3);
    assertThat(scopes.pollEventTrigger(101).getEventKey()).isEqualTo(4);
    assertThat(scopes.peekEventTrigger(101).getEventKey()).isEqualTo(5);
    transaction.commit();
    assertThat(rows("SELECT event_key FROM CAM_RU_EVENT_TRIGGER")).containsExactly(tuple(5L));
  }

  @Test
  void shouldStoreStartEventTriggersWithTheProcessInstanceToCreateAsRoot() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    insertProcess(900, "start-tenant");

    // when
    scopes.triggerStartEvent(900, 2, wrapString("start"), wrapString("b"), 200);
    scopes.triggerStartEvent(900, 1, wrapString("start"), wrapString("a"), 300);
    transaction.commit();

    // then
    assertThat(
            rows(
                "SELECT event_key, event_scope_key, element_id, process_instance_key,"
                    + " root_process_instance_key, tenant_id FROM CAM_RU_EVENT_TRIGGER"))
        .containsExactly(
            tuple(1L, 900L, "start", 300L, 300L, "start-tenant"),
            tuple(2L, 900L, "start", 200L, 200L, "start-tenant"));
    assertThat(
            scopes
                .findEventTrigger(900, trigger -> trigger.getProcessInstanceKey() == 200)
                .map(EventTrigger::getEventKey))
        .contains(2L);

    // the process is read once per transaction
    transactionContext.getCurrentTransaction();
    assertThat(
            queryCounter.queriesOf(
                () -> {
                  scopes.triggerStartEvent(900, 3, wrapString("start"), wrapString(""), 400);
                  scopes.triggerStartEvent(900, 4, wrapString("start"), wrapString(""), 500);
                }))
        .containsExactly("CAM_RU_PROCESS_DEFINITION");
  }

  @Test
  void shouldDeleteTheTriggersOfAnInstance() throws Exception {
    // given
    createCommittedScope(101);
    createCommittedScope(102);
    final var setup = transactionContext.getCurrentTransaction();
    trigger(101, 1);
    trigger(101, 2);
    trigger(101, 3);
    trigger(102, 4);
    setup.commit();
    final var transaction = transactionContext.getCurrentTransaction();
    trigger(101, 5);

    // when - the keys of the committed triggers are read with one query
    assertThat(queryCounter.queriesOf(() -> scopes.deleteInstance(101)))
        .containsExactly("CAM_RU_EVENT_TRIGGER");
    transaction.commit();

    // then
    assertThat(rows("SELECT event_scope_key FROM CAM_RU_EVENT_SCOPE")).containsExactly(tuple(102L));
    assertThat(rows("SELECT event_key FROM CAM_RU_EVENT_TRIGGER")).containsExactly(tuple(4L));
  }

  @Test
  void shouldPersistAMutatedInstanceOnUpdate() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    newElementInstance(101);
    scopes.createInstance(101, Set.of(wrapString("interrupting")), Set.of(wrapString("boundary")));
    transaction.commit();

    // when - the domain modifies the instance it got, and updates it
    final var update = transactionContext.getCurrentTransaction();
    final EventScopeInstance instance = store.getScopeInstance(101);
    instance.setInterrupted(true).setAccepting(false);
    assertThat(store.getScopeInstance(101).isInterrupted()).isFalse();
    store.updateScopeInstance(101, instance);
    update.commit();

    // then
    assertThat(
            rows(
                "SELECT event_scope_key, accepting, interrupted, root_process_instance_key"
                    + " FROM CAM_RU_EVENT_SCOPE"))
        .containsExactly(tuple(101L, false, true, ROOT_PROCESS_INSTANCE_KEY));
    final var stored = scopes.getInstance(101);
    assertThat(stored.isInterrupted()).isTrue();
    assertThat(stored.isAccepting()).isFalse();
    assertThat(stored.isInterruptingElementId(wrapString("interrupting"))).isTrue();
    assertThat(stored.isBoundaryElementId(wrapString("boundary"))).isTrue();
  }

  @Test
  void shouldRememberThatAScopeHasNoTriggersInTheTransaction() throws Exception {
    // given
    createCommittedScope(101);
    transactionContext.getCurrentTransaction();

    // when - then
    assertThat(queryCounter.queriesOf(() -> scopes.peekEventTrigger(101)))
        .containsExactly("CAM_RU_EVENT_TRIGGER");
    assertThat(
            queryCounter.queriesOf(
                () -> {
                  assertThat(scopes.peekEventTrigger(101)).isNull();
                  assertThat(scopes.findEventTrigger(101, trigger -> true)).isEmpty();
                  scopes.deleteInstance(101);
                }))
        .isEmpty();
  }

  private void createCommittedScope(final long scopeKey) throws Exception {
    final var transaction = transactionContext.getCurrentTransaction();
    newElementInstance(scopeKey);
    scopes.createInstance(scopeKey, Set.of(), Set.of());
    transaction.commit();
  }

  private void trigger(final long scopeKey, final long eventKey) {
    scopes.triggerEvent(
        scopeKey,
        eventKey,
        wrapString("event-" + eventKey),
        wrapString("{}"),
        PROCESS_INSTANCE_KEY);
  }

  private List<Long> visitedEventKeys(final long scopeKey) {
    final List<Long> visited = new ArrayList<>();
    visitedEventKeys(scopeKey, visited);
    return visited;
  }

  private void visitedEventKeys(final long scopeKey, final List<Long> visited) {
    scopes.findEventTrigger(
        scopeKey,
        trigger -> {
          assertThat(bufferAsString(trigger.getElementId()))
              .isEqualTo("event-" + trigger.getEventKey());
          visited.add(trigger.getEventKey());
          return false;
        });
  }

  private void newElementInstance(final long key) {
    elementInstanceState.newInstance(
        key,
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setRootProcessInstanceKey(ROOT_PROCESS_INSTANCE_KEY)
            .setBpmnProcessId("process")
            .setElementId("task")
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessDefinitionKey(1)
            .setTenantId(TENANT_ID),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  private void insertProcess(final long processDefinitionKey, final String tenantId) {
    session.insert(
        RdbmsProcessStore.PROCESS,
        new ProcessDefinitionRow(
            processDefinitionKey,
            tenantId,
            "process-" + processDefinitionKey,
            1,
            null,
            -1,
            "process.bpmn",
            (short) 0,
            null,
            null,
            new byte[0]));
  }

  /**
   * The committed rows of this test's partition, ordered by the first column, of a query of the
   * form {@code SELECT <columns> FROM <table>}.
   */
  private List<Tuple> rows(final String query) throws SQLException {
    final List<Tuple> rows = new ArrayList<>();
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(query + " WHERE partition_id = ? ORDER BY 1")) {
      statement.setInt(1, partitionId);
      try (final ResultSet resultSet = statement.executeQuery()) {
        final int columns = resultSet.getMetaData().getColumnCount();
        while (resultSet.next()) {
          final Object[] values = new Object[columns];
          for (int column = 1; column <= columns; column++) {
            values[column - 1] = resultSet.getObject(column);
          }
          rows.add(tuple(values));
        }
      }
    }
    return rows;
  }

  /**
   * Creates the element instance of a scope before the scope, in its transaction, and the process
   * of a start event before its trigger, as the engine has them.
   */
  private final class WithElementInstancesAndProcesses implements MutableEventScopeInstanceState {

    private final MutableEventScopeInstanceState delegate;

    private WithElementInstancesAndProcesses(final MutableEventScopeInstanceState delegate) {
      this.delegate = delegate;
    }

    @Override
    public void createInstance(
        final long eventScopeKey,
        final Collection<DirectBuffer> interruptingElementIds,
        final Collection<DirectBuffer> boundaryElementIds) {
      transactionContext.runInTransaction(
          () -> {
            newElementInstance(eventScopeKey);
            delegate.createInstance(eventScopeKey, interruptingElementIds, boundaryElementIds);
          });
    }

    @Override
    public void deleteInstance(final long eventScopeKey) {
      delegate.deleteInstance(eventScopeKey);
    }

    @Override
    public EventTrigger pollEventTrigger(final long eventScopeKey) {
      return delegate.pollEventTrigger(eventScopeKey);
    }

    @Override
    public void triggerEvent(
        final long eventScopeKey,
        final long eventKey,
        final DirectBuffer elementId,
        final DirectBuffer variables,
        final long processInstanceKey) {
      delegate.triggerEvent(eventScopeKey, eventKey, elementId, variables, processInstanceKey);
    }

    @Override
    public void triggerStartEvent(
        final long processDefinitionKey,
        final long eventKey,
        final DirectBuffer elementId,
        final DirectBuffer variables,
        final long processInstanceKey) {
      transactionContext.runInTransaction(
          () -> {
            if (session.get(RdbmsProcessStore.PROCESS, processDefinitionKey) == null) {
              insertProcess(processDefinitionKey, TENANT_ID);
            }
            delegate.triggerStartEvent(
                processDefinitionKey, eventKey, elementId, variables, processInstanceKey);
          });
    }

    @Override
    public void deleteTrigger(final long eventScopeKey, final long eventKey) {
      delegate.deleteTrigger(eventScopeKey, eventKey);
    }

    @Override
    public EventScopeInstance getInstance(final long eventScopeKey) {
      return delegate.getInstance(eventScopeKey);
    }

    @Override
    public EventTrigger peekEventTrigger(final long eventScopeKey) {
      return delegate.peekEventTrigger(eventScopeKey);
    }

    @Override
    public Optional<EventTrigger> findEventTrigger(
        final long eventScopeKey, final Predicate<EventTrigger> predicate) {
      return delegate.findEventTrigger(eventScopeKey, predicate);
    }

    @Override
    public boolean canTriggerEvent(final long eventScopeKey, final DirectBuffer elementId) {
      return delegate.canTriggerEvent(eventScopeKey, elementId);
    }
  }
}
