/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.MsgPackUtil.assertEquality;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.variable.AbstractVariableStateContractTest;
import io.camunda.zeebe.engine.state.variable.VariableStateImpl;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the variable state contract against PostgreSQL, plus the tests for what is specific to the
 * relational store: the rows and their process instance columns, rollback, scope chains from the
 * element instance path, and how many queries the lookups send.
 *
 * <p>All tests share one {@link EngineStateDatabase}; each test writes to its own partition.
 */
final class RdbmsVariableStateTest extends AbstractVariableStateContractTest {

  private static final String TENANT_ID = "variable-tenant";

  private static EngineStateDatabase database;

  private JdbcTransactionContext transactionContext;
  private int partitionId;
  private int queries;
  private MutableVariableState createdVariableState;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Override
  protected MutableElementInstanceState createElementInstanceState() {
    database.observed().reset();
    database.observed().decorate(this::countingQueries);
    transactionContext = new JdbcTransactionContext();
    partitionId = database.nextPartitionId();
    final var session = database.entitySession(transactionContext, partitionId);
    createdVariableState = new VariableStateImpl(new RdbmsVariableStore(session));
    return new ElementInstanceStateImpl(
        new RdbmsElementInstanceStore(session), createdVariableState);
  }

  @Override
  protected MutableVariableState variableState() {
    return createdVariableState;
  }

  @AfterEach
  void endTransaction() throws Exception {
    transactionContext.getCurrentTransaction().rollback();
    database.observed().reset();
  }

  @Test
  void shouldWriteRowsWithProcessInstanceOfScopeOnCommit() throws Exception {
    // given - a called process instance 200 with a sub-process 201, in the tree of 100
    final var transaction = transactionContext.getCurrentTransaction();
    createScope(200, -1, 100, BpmnElementType.PROCESS, List.of(List.of(100L, 101L), List.of(200L)));
    createScope(
        201,
        200,
        100,
        BpmnElementType.SUB_PROCESS,
        List.of(List.of(100L, 101L), List.of(200L, 201L)));

    // when - set without record, and from the record of an event
    variableState.setVariableLocal(1, 201, PROCESS_KEY, wrapString("a"), asMsgPack("1"));
    variableState.setVariableLocal(
        2,
        new VariableRecord()
            .setScopeKey(200)
            .setName(wrapString("b"))
            .setValue(asMsgPack("2"))
            .setProcessInstanceKey(200)
            .setRootProcessInstanceKey(100)
            .setProcessDefinitionKey(PROCESS_KEY)
            .setTenantId(TENANT_ID));
    assertThat(rows("SELECT name FROM CAM_RU_VARIABLE")).isEmpty();
    transaction.commit();

    // then
    assertThat(
            rows(
                "SELECT name, scope_key, variable_key, process_instance_key,"
                    + " root_process_instance_key, tenant_id FROM CAM_RU_VARIABLE"))
        .containsExactly(
            tuple("a", 201L, 1L, 200L, 100L, TENANT_ID),
            tuple("b", 200L, 2L, 200L, 100L, TENANT_ID));
  }

  @Test
  void shouldTakeProcessInstanceFromRecordForScopeWithoutElementInstance() throws Exception {
    // given - like the input element of a multi-instance inner instance, before its activation
    final var transaction = transactionContext.getCurrentTransaction();

    // when
    variableState.setVariableLocal(
        1,
        new VariableRecord()
            .setScopeKey(300)
            .setName(wrapString("item"))
            .setValue(asMsgPack("1"))
            .setProcessInstanceKey(250)
            .setRootProcessInstanceKey(-1)
            .setProcessDefinitionKey(PROCESS_KEY)
            .setTenantId(TENANT_ID));
    transaction.commit();

    // then - a record without root key belongs to a root process instance
    assertThat(
            rows(
                "SELECT scope_key, process_instance_key, root_process_instance_key, tenant_id"
                    + " FROM CAM_RU_VARIABLE"))
        .containsExactly(tuple(300L, 250L, 250L, TENANT_ID));
    assertThat(variableState.getParentScopeKey(300)).isEqualTo(-1L);
    assertEquality(variableState.getVariable(300, wrapString("item")), "1");
  }

  @Test
  void shouldRejectVariableWithoutRecordInScopeWithoutElementInstance() {
    // when - then
    assertThatThrownBy(
            () ->
                variableState.setVariableLocal(
                    1, 300, PROCESS_KEY, wrapString("a"), asMsgPack("1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected to set variable 'a' in scope 300, but the scope's element instance does not"
                + " exist");
  }

  @Test
  void shouldOverwriteCommittedVariable() throws Exception {
    // given
    createProcessTree();
    variableState.setVariableLocal(1, 100, PROCESS_KEY, wrapString("a"), asMsgPack("1"));

    // when
    final var transaction = transactionContext.getCurrentTransaction();
    variableState.setVariableLocal(2, 100, PROCESS_KEY, wrapString("a"), asMsgPack("2"));
    transaction.commit();

    // then
    assertThat(rows("SELECT name, variable_key FROM CAM_RU_VARIABLE"))
        .containsExactly(tuple("a", 2L));
    assertEquality(variableState.getVariableLocal(100, wrapString("a")), "2");
  }

  @Test
  void shouldLeaveNothingOnRollback() throws Exception {
    // given
    createProcessTree();
    final var transaction = transactionContext.getCurrentTransaction();
    variableState.setVariableLocal(1, 200, PROCESS_KEY, wrapString("a"), asMsgPack("1"));
    assertEquality(variableState.getVariable(202, wrapString("a")), "1");

    // when
    transaction.rollback();

    // then
    assertThat(variableState.getVariable(202, wrapString("a"))).isNull();
    assertThat(rows("SELECT name FROM CAM_RU_VARIABLE")).isEmpty();
  }

  @Test
  void shouldDeleteVariablesOfRemovedScopeInSameTransaction() throws Exception {
    // given
    createProcessTree();
    variableState.setVariableLocal(1, 201, PROCESS_KEY, wrapString("a"), asMsgPack("1"));
    variableState.setVariableLocal(2, 202, PROCESS_KEY, wrapString("b"), asMsgPack("2"));
    variableState.setVariableLocal(3, 100, PROCESS_KEY, wrapString("c"), asMsgPack("3"));

    // when
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.removeInstance(202);
    elementInstanceState.removeInstance(201);
    transaction.commit();

    // then
    assertThat(rows("SELECT name FROM CAM_RU_VARIABLE")).containsExactly(tuple("c"));
  }

  @Test
  void shouldNotSeeVariablesOfCallingProcessInstance() {
    // given
    createProcessTree();
    variableState.setVariableLocal(1, 100, PROCESS_KEY, wrapString("root"), asMsgPack("1"));
    variableState.setVariableLocal(2, 101, PROCESS_KEY, wrapString("call"), asMsgPack("2"));
    variableState.setVariableLocal(3, 200, PROCESS_KEY, wrapString("child"), asMsgPack("3"));
    variableState.setVariableLocal(4, 201, PROCESS_KEY, wrapString("sub"), asMsgPack("4"));
    variableState.setVariableLocal(5, 202, PROCESS_KEY, wrapString("sub"), asMsgPack("5"));

    // when - then
    assertThat(variableState.getVariable(202, wrapString("root"))).isNull();
    assertThat(variableState.getVariable(202, wrapString("call"))).isNull();
    assertEquality(variableState.getVariable(202, wrapString("child")), "3");
    assertEquality(variableState.getVariable(202, wrapString("sub")), "5");
    assertEquality(variableState.getVariablesAsDocument(202), "{'child': 3, 'sub': 5}");
    assertEquality(
        variableState.getVariablesAsDocument(
            202, List.of(wrapString("root"), wrapString("child"), wrapString("sub"))),
        "{'child': 3, 'sub': 5}");
    assertEquality(variableState.getVariablesAsDocument(101), "{'root': 1, 'call': 2}");
  }

  @Test
  void shouldNotSeeLocalVariablesOfMultiInstanceSiblings() {
    // given - two inner instances 204 and 205 of the multi-instance body 203
    createProcessTree();
    final var childPath = List.of(100L, 101L);
    createScope(
        203,
        201,
        100,
        BpmnElementType.MULTI_INSTANCE_BODY,
        List.of(childPath, List.of(200L, 201L, 203L)));
    createScope(
        204,
        203,
        100,
        BpmnElementType.SERVICE_TASK,
        List.of(childPath, List.of(200L, 201L, 203L, 204L)));
    createScope(
        205,
        203,
        100,
        BpmnElementType.SERVICE_TASK,
        List.of(childPath, List.of(200L, 201L, 203L, 205L)));
    variableState.setVariableLocal(1, 203, PROCESS_KEY, wrapString("items"), asMsgPack("[1,2]"));
    variableState.setVariableLocal(2, 204, PROCESS_KEY, wrapString("item"), asMsgPack("1"));
    variableState.setVariableLocal(3, 205, PROCESS_KEY, wrapString("item"), asMsgPack("2"));
    variableState.setVariableLocal(4, 204, PROCESS_KEY, wrapString("own"), asMsgPack("4"));

    // when - then
    assertEquality(variableState.getVariable(204, wrapString("item")), "1");
    assertEquality(variableState.getVariable(205, wrapString("item")), "2");
    assertThat(variableState.getVariable(205, wrapString("own"))).isNull();
    assertEquality(variableState.getVariablesAsDocument(205), "{'item': 2, 'items': [1, 2]}");
  }

  @Test
  void shouldLookUpVariableAlongChainWithOneQuery() throws Exception {
    // given - committed variables in all levels of the chain of 202
    createProcessTree();
    variableState.setVariableLocal(1, 200, PROCESS_KEY, wrapString("a"), asMsgPack("1"));
    variableState.setVariableLocal(2, 201, PROCESS_KEY, wrapString("b"), asMsgPack("2"));
    variableState.setVariableLocal(3, 202, PROCESS_KEY, wrapString("c"), asMsgPack("3"));
    transactionContext.getCurrentTransaction();
    elementInstanceState.getInstance(202);

    // when - then: one query for the variable in all scopes
    assertThat(queriesOf(() -> variableState.getVariable(202, wrapString("a")))).isOne();
    // a repeated lookup is answered from the transaction's reads, also a missing variable
    assertThat(queriesOf(() -> variableState.getVariable(202, wrapString("a")))).isZero();
    assertThat(queriesOf(() -> variableState.getVariable(202, wrapString("x")))).isOne();
    assertThat(queriesOf(() -> variableState.getVariable(202, wrapString("x")))).isZero();
    assertThat(queriesOf(() -> variableState.getVariable(201, wrapString("a")))).isOne();
    assertThat(queriesOf(() -> variableState.getVariable(201, wrapString("a")))).isZero();
  }

  @Test
  void shouldReadDocumentOfChainWithOneQuery() throws Exception {
    // given
    createProcessTree();
    variableState.setVariableLocal(1, 200, PROCESS_KEY, wrapString("a"), asMsgPack("1"));
    variableState.setVariableLocal(2, 201, PROCESS_KEY, wrapString("b"), asMsgPack("2"));
    variableState.setVariableLocal(3, 202, PROCESS_KEY, wrapString("c"), asMsgPack("3"));
    transactionContext.getCurrentTransaction();
    elementInstanceState.getInstance(202);

    // when - then: one query for all scopes of the chain
    assertThat(queriesOf(() -> variableState.getVariablesAsDocument(202))).isOne();
    // the scopes are loaded completely, so every lookup in them is answered from the reads
    assertThat(queriesOf(() -> variableState.getVariable(202, wrapString("a")))).isZero();
    assertThat(queriesOf(() -> variableState.getVariable(202, wrapString("x")))).isZero();
    assertThat(
            queriesOf(
                () ->
                    variableState.getVariablesAsDocument(
                        202, List.of(wrapString("a"), wrapString("x")))))
        .isZero();
    assertThat(queriesOf(() -> variableState.getVariablesLocalAsDocument(201))).isZero();
    assertThat(queriesOf(() -> variableState.removeAllVariables(201))).isZero();
  }

  @Test
  void shouldSeeStagedWritesInLoadedScopes() throws Exception {
    // given - the scopes of 202 are loaded
    createProcessTree();
    variableState.setVariableLocal(1, 200, PROCESS_KEY, wrapString("a"), asMsgPack("1"));
    variableState.setVariableLocal(2, 201, PROCESS_KEY, wrapString("b"), asMsgPack("2"));
    transactionContext.getCurrentTransaction();
    assertEquality(variableState.getVariablesAsDocument(202), "{'a': 1, 'b': 2}");

    // when
    variableState.setVariableLocal(3, 202, PROCESS_KEY, wrapString("a"), asMsgPack("3"));
    variableState.removeAllVariables(201);

    // then
    assertEquality(variableState.getVariablesAsDocument(202), "{'a': 3}");
    assertEquality(variableState.getVariable(201, wrapString("a")), "1");
    assertThat(variableState.getVariable(201, wrapString("b"))).isNull();
  }

  @Test
  void shouldVisitVariablesInOrderOfRocksDbKeys() {
    // given
    createProcessTree();
    for (final String name : List.of("bb", "b", "a", "ab", "aaa")) {
      variableState.setVariableLocal(
          name.hashCode(), 100, PROCESS_KEY, wrapString(name), asMsgPack("1"));
    }

    // when
    final var names =
        variableState.getVariablesLocal(100).stream()
            .map(variable -> bufferAsString(variable.name()))
            .toList();

    // then - by length, then bytes
    assertThat(names).containsExactly("a", "b", "ab", "bb", "aaa");
  }

  @Test
  void shouldStoreVariableDocumentStateWithProcessInstanceOfScope() throws Exception {
    // given
    createProcessTree();
    final var transaction = transactionContext.getCurrentTransaction();

    // when
    variableState.storeVariableDocumentState(
        7,
        new VariableDocumentRecord()
            .setScopeKey(202)
            .setVariables(asMsgPack("{'a': 1}"))
            .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE));
    transaction.commit();

    // then
    assertThat(
            rows(
                "SELECT scope_key, process_instance_key, root_process_instance_key, tenant_id"
                    + " FROM CAM_RU_VARIABLE_DOCUMENT_STATE"))
        .containsExactly(tuple(202L, 200L, 100L, TENANT_ID));
    assertThat(variableState.findVariableDocumentState(202))
        .hasValueSatisfying(state -> assertThat(state.getKey()).isEqualTo(7));
  }

  @Test
  void shouldRejectElementInstanceWithoutPath() {
    // given - an element instance whose record has no element instance path
    elementInstanceState.newInstance(
        400,
        new ProcessInstanceRecord()
            .setProcessInstanceKey(400)
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setTenantId(TENANT_ID),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when - then
    assertThatThrownBy(() -> variableState.getVariable(400, wrapString("a")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            "Expected element instance 400 to have an element instance path that ends with it");
  }

  /**
   * Creates a committed process instance tree: root instance 100 with a call activity 101, which
   * called the child instance 200 with a sub-process 201 and a task 202 in it. The chain of 202 is
   * 202, 201, 200: the called instance has no parent element instance.
   */
  private void createProcessTree() {
    createScope(100, -1, 100, BpmnElementType.PROCESS, List.of(List.of(100L)));
    createScope(101, 100, 100, BpmnElementType.CALL_ACTIVITY, List.of(List.of(100L, 101L)));
    final var callPath = List.of(100L, 101L);
    createScope(200, -1, 100, BpmnElementType.PROCESS, List.of(callPath, List.of(200L)));
    createScope(201, 200, 100, BpmnElementType.SUB_PROCESS, List.of(callPath, List.of(200L, 201L)));
    createScope(
        202, 201, 100, BpmnElementType.SERVICE_TASK, List.of(callPath, List.of(200L, 201L, 202L)));
  }

  private void createScope(
      final long key,
      final long flowScopeKey,
      final long rootProcessInstanceKey,
      final BpmnElementType type,
      final List<List<Long>> path) {
    final long processInstanceKey = path.getLast().getFirst();
    final var record =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setRootProcessInstanceKey(rootProcessInstanceKey)
            .setFlowScopeKey(flowScopeKey)
            .setBpmnElementType(type)
            .setTenantId(TENANT_ID)
            .setElementInstancePath(path);
    elementInstanceState.newInstance(
        elementInstanceState.getInstance(flowScopeKey),
        key,
        record,
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private int queriesOf(final Runnable lookup) {
    queries = 0;
    lookup.run();
    return queries;
  }

  /**
   * The committed rows of this test's partition, ordered by the first column, of a query of the
   * form {@code SELECT <columns> FROM <table>}.
   */
  private List<Tuple> rows(final String query) throws SQLException {
    final List<Tuple> rows = new ArrayList<>();
    try (final var observer = database.dataSource().getConnection();
        final var statement =
            observer.prepareStatement(query + " WHERE partition_id = ? ORDER BY 1")) {
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

  /** Counts the statements that the connection executes outside of batches, i.e. the queries. */
  private Connection countingQueries(final Connection target) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              final Object result = invoke(target, method, args);
              if (method.getName().equals("prepareStatement")
                  && result instanceof final PreparedStatement statement) {
                return countingStatement(statement);
              }
              return result;
            });
  }

  private PreparedStatement countingStatement(final PreparedStatement target) {
    return (PreparedStatement)
        Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
              if (method.getName().startsWith("execute")
                  && !method.getName().equals("executeBatch")) {
                queries++;
              }
              return invoke(target, method, args);
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
