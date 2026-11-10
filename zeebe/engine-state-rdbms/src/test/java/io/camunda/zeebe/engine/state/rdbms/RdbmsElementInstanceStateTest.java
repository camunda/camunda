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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.AbstractElementInstanceStateContractTest;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateTest;
import io.camunda.zeebe.engine.state.instance.JobStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.variable.VariableStateImpl;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Runs the element instance state contract against PostgreSQL, plus the tests for what is specific
 * to staging writes until commit.
 *
 * <p>The variable state is the relational one on the same session, like in the broker, so the
 * scopes and variables of the element instances take part in the same JDBC transaction.
 *
 * <p>All tests share one {@link EngineStateDatabase}; each test writes to its own partition to stay
 * isolated. Add {@code -Dzeebe.engine.rdbms.dumpLevel=debug} to log each test's rows with the
 * decoded payload (see {@link ElementInstanceTableDump}).
 */
@ExtendWith(ProcessingStateExtension.class)
final class RdbmsElementInstanceStateTest extends AbstractElementInstanceStateContractTest {

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
        AFTER INSERT OR UPDATE OR DELETE ON CAM_RU_ELEMENT_INSTANCE
        FOR EACH ROW EXECUTE FUNCTION log_element_instance_write();
      """;

  private static final String TREE_TENANT_ID = "tree-tenant";

  private static EngineStateDatabase database;
  private static PGSimpleDataSource dataSource;

  private MutableProcessingState processingState;
  private final ConnectionSpy connectionSpy = new ConnectionSpy();
  private JdbcTransactionContext transactionContext;
  private int partitionId;
  private MutableVariableState variableState;
  private MutableJobState jobState;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
    dataSource = database.dataSource();
    try (final var setupConnection = dataSource.getConnection();
        final var statement = setupConnection.createStatement()) {
      if (!EngineStateDatabase.tableExists(setupConnection, "element_instance_write_log")) {
        statement.execute(WRITE_LOG_DDL);
      }
    }
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Override
  protected MutableElementInstanceState createElementInstanceState() {
    database.observed().reset();
    database.observed().decorate(connectionSpy::proxy);
    transactionContext = new JdbcTransactionContext();
    partitionId = database.nextPartitionId();
    final var session = database.entitySession(transactionContext, partitionId);
    variableState = new VariableStateImpl(new RdbmsVariableStore(session));
    jobState = new JobStateImpl(new RdbmsJobStore(session));
    return new ElementInstanceStateImpl(new RdbmsElementInstanceStore(session), variableState);
  }

  @Override
  protected MutableVariableState variableState() {
    return variableState;
  }

  @Override
  protected void assertNoStateLeft() {
    for (final String table : RdbmsEngineStateSchema.TABLES) {
      assertThat(countRows(table)).describedAs("Expected table %s to be empty", table).isZero();
    }
    ElementInstanceStateTest.assertNoColumnFamilyLeft(processingState);
  }

  @AfterEach
  void endTransaction() throws Exception {
    if (transactionContext != null) {
      transactionContext.getCurrentTransaction().rollback();
    }
    database.observed().reset();
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
  void shouldDiscardWritesOfAllTablesOnRollback() throws Exception {
    // given
    elementInstanceState.newInstance(
        100, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(100)).isOne();

    // when
    transaction.rollback();

    // then
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(100)).isZero();
  }

  @Test
  void shouldWriteAllTablesExactlyOnceWhenCommitIsRetried() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    elementInstanceState.newInstance(
        100,
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    elementInstanceState.setAwaitResultRequestMetadata(
        100,
        TENANT_ID,
        new AwaitProcessInstanceResultMetadata().setRequestId(1).setRequestStreamId(2));
    elementInstanceState.addRuntimeInstructions(
        100,
        TENANT_ID,
        List.of(
            new ProcessInstanceCreationRuntimeInstruction()
                .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                .setAfterElementId("task")));
    variableState.setVariableLocal(1, 100, 1, wrapString("a"), MsgPackUtil.asMsgPack("1"));
    variableState.storeVariableDocumentState(2, new VariableDocumentRecord().setScopeKey(100));
    jobState.insertJobRecordActivatable(
        3, new JobRecord().setType("task").setTenantId(TENANT_ID).setProcessInstanceKey(100));
    connectionSpy.failNextCommit = true;

    // when - the first commit fails after all statements were sent, and is rolled back
    assertThatThrownBy(transaction::commit).isInstanceOf(ZeebeDbException.class);
    assertThat(committedRowCounts())
        .containsOnlyKeys(RdbmsEngineStateSchema.PROCESS_INSTANCE_TABLES)
        .allSatisfy((table, count) -> assertThat(count).describedAs(table).isZero());
    transaction.commit();

    // then
    assertThat(committedRowCounts())
        .allSatisfy((table, count) -> assertThat(count).describedAs(table).isOne());
    assertThat(committedTimesTaken(100)).isEqualTo(2);
  }

  @Test
  void shouldCommitThroughTheSessionAndReturnTheConnectionToThePool() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();

    // when - every kind of read and write
    final var processInstance =
        createProcessInstanceRecord().setBpmnElementType(BpmnElementType.PROCESS);
    final var parent =
        elementInstanceState.newInstance(
            100, processInstance, ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        parent, 101, createProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATING);
    elementInstanceState.getChildren(100);
    elementInstanceState.forEachChildKey(100, key -> true);
    elementInstanceState.getActiveProcessInstanceCount();
    elementInstanceState.hasActiveProcessInstances(
        processInstance.getProcessDefinitionKey(), List.of());
    elementInstanceState.getProcessInstanceKeysByDefinitionKey(
        processInstance.getProcessDefinitionKey());
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow"));
    elementInstanceState.getNumberOfTakenSequenceFlows(100);
    elementInstanceState.getAwaitResultRequestMetadata(100);
    elementInstanceState.getRuntimeInstructionsForElementId(100, "task");
    elementInstanceState.insertProcessInstanceKeyByBusinessId("order-1", "process", "", 100L);
    elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        "order-1", "process", "", key -> false);
    assertThat(connectionSpy.transactionCalls).doesNotContain("commit", "rollback", "close");
    transaction.commit();

    // then
    assertThat(connectionSpy.transactionCalls)
        .containsOnlyOnce("commit", "close")
        .doesNotContain("rollback");
    assertThat(database.observed().borrowed()).isOne();
    assertThat(database.observed().returned()).isOne();
    assertThat(committedRows()).extracting(Row::key).containsExactly(100L, 101L);
  }

  @Test
  void shouldStoreMetadataOfElementInstances() {
    // given - a call activity in root instance 100 that called child instance 200
    createProcessTree(100, 200, 1);

    // when
    final var rows =
        metadataRows(
            "SELECT element_instance_key, process_instance_key, root_process_instance_key,"
                + " tenant_id, bpmn_process_id, element_id FROM CAM_RU_ELEMENT_INSTANCE");

    // then
    assertThat(rows)
        .containsExactly(
            tuple(100L, 100L, 100L, TREE_TENANT_ID, "parent", "parent"),
            tuple(101L, 100L, 100L, TREE_TENANT_ID, "parent", "call"),
            tuple(200L, 200L, 100L, TREE_TENANT_ID, "child", "child"),
            tuple(201L, 200L, 100L, TREE_TENANT_ID, "child", "subprocess"));
  }

  @Test
  void shouldUseProcessInstanceKeyAsRootOfRecordWithoutRootKey() {
    // given - a record without root key, like one written before the record carried it
    final var record = createProcessInstanceRecord().setProcessInstanceKey(300);
    assertThat(record.getRootProcessInstanceKey()).isEqualTo(-1);

    // when
    elementInstanceState.newInstance(301, record, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // then
    assertThat(
            metadataRows(
                "SELECT element_instance_key, process_instance_key, root_process_instance_key"
                    + " FROM CAM_RU_ELEMENT_INSTANCE"))
        .containsExactly(tuple(301L, 300L, 300L));
  }

  @Test
  void shouldKeepMetadataOfElementInstanceUpToDate() {
    // given
    final var record = createProcessInstanceRecord().setProcessInstanceKey(300);
    final var instance =
        elementInstanceState.newInstance(301, record, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when - e.g. migrated to another process
    instance.getValue().setBpmnProcessId("migrated").setElementId("migratedElement");
    elementInstanceState.updateInstance(instance);

    // then
    assertThat(
            metadataRows(
                "SELECT element_instance_key, bpmn_process_id, element_id"
                    + " FROM CAM_RU_ELEMENT_INSTANCE"))
        .containsExactly(tuple(301L, "migrated", "migratedElement"));
  }

  @Test
  void shouldStoreMetadataOfFlowScopeOnTakenSequenceFlows() {
    // given
    createProcessTree(100, 200, 1);

    // when - in the root instance and in the sub-process of the called instance
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        100, wrapString("gateway"), wrapString("flow-in-root"));
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        201, wrapString("gateway"), wrapString("flow-in-child"));
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        201, wrapString("gateway"), wrapString("flow-in-child"));

    // then
    assertThat(
            metadataRows(
                "SELECT sequence_flow_element_id, process_instance_key,"
                    + " root_process_instance_key, tenant_id, times_taken"
                    + " FROM CAM_RU_TAKEN_SEQUENCE_FLOW"))
        .containsExactly(
            tuple("flow-in-child", 200L, 100L, TREE_TENANT_ID, 2),
            tuple("flow-in-root", 100L, 100L, TREE_TENANT_ID, 1));
  }

  @Test
  void shouldRejectTakingSequenceFlowInMissingFlowScope() {
    // when / then
    assertThatThrownBy(
            () ->
                elementInstanceState.incrementNumberOfTakenSequenceFlows(
                    100, wrapString("gateway"), wrapString("flow")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected to take sequence flow 'flow' to gateway 'gateway' in flow scope 100,"
                + " but the flow scope does not exist");
    assertThat(countRows("CAM_RU_TAKEN_SEQUENCE_FLOW")).isZero();
  }

  @Test
  void shouldStoreMetadataOfProcessInstanceBeingCreated() {
    // given - the process instance is created, so it has no element instance yet

    // when
    elementInstanceState.setAwaitResultRequestMetadata(
        300, TENANT_ID, new AwaitProcessInstanceResultMetadata().setRequestId(1));
    elementInstanceState.addRuntimeInstructions(
        300,
        TENANT_ID,
        List.of(
            new ProcessInstanceCreationRuntimeInstruction()
                .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                .setAfterElementId("task")));

    // then
    final var expected = tuple(300L, 300L, TENANT_ID);
    assertThat(
            metadataRows(
                "SELECT process_instance_key, root_process_instance_key, tenant_id"
                    + " FROM CAM_RU_AWAIT_PROCESS_INSTANCE_RESULT"))
        .containsExactly(expected);
    assertThat(
            metadataRows(
                "SELECT process_instance_key, root_process_instance_key, tenant_id"
                    + " FROM CAM_RU_RUNTIME_INSTRUCTIONS"))
        .containsExactly(expected);
  }

  @Test
  void shouldStoreBusinessIdOfProcessInstancesButFindRootInstancesOnly() throws Exception {
    // given - a root instance with a business id, whose called children and elements carry it too
    transactionContext.getCurrentTransaction();
    final var root =
        elementInstanceState.newInstance(
            100,
            treeRecord(100, 100, "parent", "parent", BpmnElementType.PROCESS, 1)
                .setBusinessId("order-1"),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        root,
        101,
        treeRecord(100, 100, "parent", "call", BpmnElementType.CALL_ACTIVITY, 1)
            .setBusinessId("order-1"),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        200,
        treeRecord(200, 100, "child", "child", BpmnElementType.PROCESS, 1)
            .setParentProcessInstanceKey(100)
            .setParentElementInstanceKey(101)
            .setBusinessId("order-1"),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    // a child called by an instance created before records carried the root process instance key,
    // so its root key falls back to its own key like a root instance's
    elementInstanceState.newInstance(
        400,
        treeRecord(400, -1, "child", "child", BpmnElementType.PROCESS, 1)
            .setParentProcessInstanceKey(100)
            .setParentElementInstanceKey(101)
            .setBusinessId("order-1"),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when - and a root instance without business id
    elementInstanceState.newInstance(
        300,
        treeRecord(300, 300, "parent", "parent", BpmnElementType.PROCESS, 1),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // then - only the root instance is found, like by the RocksDB business id index, which has no
    // entries for called instances
    assertThat(hasProcessInstanceWithBusinessId("order-1", "parent")).isTrue();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "child")).isFalse();
    commitAndBeginTransaction();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "parent")).isTrue();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "child")).isFalse();
    assertThat(
            metadataRows(
                "SELECT element_instance_key, parent_process_instance_key, business_id"
                    + " FROM CAM_RU_ELEMENT_INSTANCE"))
        .containsExactly(
            tuple(100L, -1L, "order-1"),
            tuple(101L, -1L, null),
            tuple(200L, 100L, "order-1"),
            tuple(300L, -1L, null),
            tuple(400L, 100L, "order-1"));
  }

  @Test
  void shouldFindProcessInstanceByBusinessIdUntilItIsRemoved() throws Exception {
    // given - a root instance with a business id, committed
    transactionContext.getCurrentTransaction();
    elementInstanceState.newInstance(
        100,
        treeRecord(100, 100, "process", "process", BpmnElementType.PROCESS, 1)
            .setBusinessId("order-1"),
        ProcessInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(hasProcessInstanceWithBusinessId("order-1", "process")).isTrue();
    commitAndBeginTransaction();

    // when - it is completing: RocksDB keeps the index entry until the instance is removed
    elementInstanceState.updateInstance(
        100, instance -> instance.setState(ProcessInstanceIntent.ELEMENT_COMPLETING));

    // then
    assertThat(hasProcessInstanceWithBusinessId("order-1", "process")).isTrue();

    // when - same calls as the ProcessInstanceElementCompleted applier
    elementInstanceState.removeInstance(100);
    elementInstanceState.deleteProcessInstanceKeyMappingByBusinessId(
        "order-1", "process", TREE_TENANT_ID, 100);

    // then - the staged removal hides the committed row
    assertThat(hasProcessInstanceWithBusinessId("order-1", "process")).isFalse();
    commitAndBeginTransaction();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "process")).isFalse();
  }

  @Test
  void shouldFindProcessInstanceByLateAssignedBusinessId() throws Exception {
    // given - a root instance without business id, committed
    transactionContext.getCurrentTransaction();
    elementInstanceState.newInstance(
        100,
        treeRecord(100, 100, "process", "process", BpmnElementType.PROCESS, 1),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    commitAndBeginTransaction();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "process")).isFalse();

    // when - same calls as the ProcessInstanceBusinessIdAssigned applier
    elementInstanceState.updateInstance(
        100, instance -> instance.getValue().setBusinessId("order-1"));
    elementInstanceState.insertProcessInstanceKeyByBusinessId(
        "order-1", "process", TREE_TENANT_ID, 100);

    // then - staged and committed
    assertThat(hasProcessInstanceWithBusinessId("order-1", "process")).isTrue();
    commitAndBeginTransaction();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "process")).isTrue();
    assertThat(
            metadataRows("SELECT element_instance_key, business_id FROM CAM_RU_ELEMENT_INSTANCE"))
        .containsExactly(tuple(100L, "order-1"));
  }

  @Test
  void shouldMoveBusinessIdToTargetProcessIdOnMigration() throws Exception {
    // given - a root instance with a business id, committed
    transactionContext.getCurrentTransaction();
    elementInstanceState.newInstance(
        100,
        treeRecord(100, 100, "source", "source", BpmnElementType.PROCESS, 1)
            .setBusinessId("order-1"),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    commitAndBeginTransaction();

    // when - same calls as the ProcessInstanceElementMigrated applier
    elementInstanceState.updateInstance(
        100,
        instance ->
            instance.getValue().setBpmnProcessId("target").setElementId("target").setVersion(2));
    elementInstanceState.deleteProcessInstanceKeyMappingByBusinessId(
        "order-1", "source", TREE_TENANT_ID, 100);
    elementInstanceState.insertProcessInstanceKeyByBusinessId(
        "order-1", "target", TREE_TENANT_ID, 100);

    // then - the staged update hides the committed row of the source process id
    assertThat(hasProcessInstanceWithBusinessId("order-1", "source")).isFalse();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "target")).isTrue();
    commitAndBeginTransaction();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "source")).isFalse();
    assertThat(hasProcessInstanceWithBusinessId("order-1", "target")).isTrue();
  }

  /** Commits the open transaction and begins the next one, so the following writes are staged. */
  private void commitAndBeginTransaction() throws Exception {
    transactionContext.getCurrentTransaction().commit();
    transactionContext.getCurrentTransaction();
  }

  private boolean hasProcessInstanceWithBusinessId(
      final String businessId, final String bpmnProcessId) {
    return elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        businessId, bpmnProcessId, TREE_TENANT_ID, key -> false);
  }

  @Test
  void shouldFindAllRowsOfProcessTreeByRootKey() {
    // given - two process trees with rows in every table
    createProcessTree(100, 200, 1);
    createProcessTree(500, 600, 2);
    for (final long rootKey : List.of(100L, 500L)) {
      elementInstanceState.setAwaitResultRequestMetadata(
          rootKey, TREE_TENANT_ID, new AwaitProcessInstanceResultMetadata().setRequestId(1));
      elementInstanceState.addRuntimeInstructions(
          rootKey,
          TREE_TENANT_ID,
          List.of(
              new ProcessInstanceCreationRuntimeInstruction()
                  .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
                  .setAfterElementId("task")));
      elementInstanceState.incrementNumberOfTakenSequenceFlows(
          rootKey, wrapString("gateway"), wrapString("flow"));
      // the sub-process of the called instance
      elementInstanceState.incrementNumberOfTakenSequenceFlows(
          rootKey + 101, wrapString("gateway"), wrapString("flow"));
      variableState.setVariableLocal(
          rootKey, rootKey, 1, wrapString("inRoot"), MsgPackUtil.asMsgPack("1"));
      variableState.setVariableLocal(
          rootKey + 101, rootKey + 101, 1, wrapString("inChild"), MsgPackUtil.asMsgPack("2"));
      variableState.storeVariableDocumentState(
          rootKey + 1, new VariableDocumentRecord().setScopeKey(rootKey + 101));
      // a job of the called instance's sub-process
      jobState.insertJobRecordActivatable(
          rootKey + 2,
          new JobRecord()
              .setType("task")
              .setTenantId(TREE_TENANT_ID)
              .setProcessInstanceKey(rootKey + 100)
              .setRootProcessInstanceKey(rootKey)
              .setElementInstanceKey(rootKey + 101));
    }

    // when
    final Map<String, Long> rowsOfTree = new LinkedHashMap<>();
    for (final String table : RdbmsEngineStateSchema.PROCESS_INSTANCE_TABLES) {
      rowsOfTree.put(table, countRows(table, "root_process_instance_key = 100"));
    }

    // then
    assertThat(rowsOfTree)
        .containsExactly(
            entry("CAM_RU_ELEMENT_INSTANCE", 4L),
            entry("CAM_RU_TAKEN_SEQUENCE_FLOW", 2L),
            entry("CAM_RU_AWAIT_PROCESS_INSTANCE_RESULT", 1L),
            entry("CAM_RU_RUNTIME_INSTRUCTIONS", 1L),
            entry("CAM_RU_VARIABLE", 2L),
            entry("CAM_RU_VARIABLE_DOCUMENT_STATE", 1L),
            entry("CAM_RU_JOB", 1L));
    assertThat(committedRowCounts()).containsEntry("CAM_RU_ELEMENT_INSTANCE", 8L);
  }

  /**
   * Creates a process instance tree: root instance {@code rootKey} with a call activity ({@code
   * rootKey + 1}), which called the child instance {@code childKey} with a sub-process ({@code
   * childKey + 1}). Like in the engine, the called instance has no parent element instance.
   */
  private void createProcessTree(final long rootKey, final long childKey, final int version) {
    final var root =
        elementInstanceState.newInstance(
            rootKey,
            treeRecord(rootKey, -1, "parent", "parent", BpmnElementType.PROCESS, version),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        root,
        rootKey + 1,
        treeRecord(rootKey, rootKey, "parent", "call", BpmnElementType.CALL_ACTIVITY, version),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var child =
        elementInstanceState.newInstance(
            childKey,
            treeRecord(childKey, rootKey, "child", "child", BpmnElementType.PROCESS, version)
                .setParentProcessInstanceKey(rootKey)
                .setParentElementInstanceKey(rootKey + 1),
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        child,
        childKey + 1,
        treeRecord(childKey, rootKey, "child", "subprocess", BpmnElementType.SUB_PROCESS, version),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private static ProcessInstanceRecord treeRecord(
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final String bpmnProcessId,
      final String elementId,
      final BpmnElementType type,
      final int version) {
    return new ProcessInstanceRecord()
        .setProcessInstanceKey(processInstanceKey)
        .setRootProcessInstanceKey(rootProcessInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setElementId(elementId)
        .setBpmnElementType(type)
        .setProcessDefinitionKey(version)
        .setVersion(version)
        .setTenantId(TREE_TENANT_ID);
  }

  /**
   * The committed rows of this test's partition, ordered by the first column, of a query of the
   * form {@code SELECT <columns> FROM <table>}.
   */
  private List<Tuple> metadataRows(final String query) {
    final List<Tuple> rows = new ArrayList<>();
    try (final var freshConnection = dataSource.getConnection();
        final var statement =
            freshConnection.prepareStatement(query + " WHERE partition_id = ? ORDER BY 1")) {
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
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
    return rows;
  }

  private List<Row> committedRows() {
    final List<Row> rows = new ArrayList<>();
    try (final var freshConnection = dataSource.getConnection();
        final var statement =
            freshConnection.prepareStatement(
                "SELECT element_instance_key, parent_key, process_instance_key,"
                    + " process_definition_key, bpmn_element_type, state, payload"
                    + " FROM CAM_RU_ELEMENT_INSTANCE WHERE partition_id = ?"
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
    return countRows(table, "TRUE");
  }

  private long countRows(final String table, final String condition) {
    return database.countRows(table, partitionId, condition);
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

  private Map<String, Long> committedRowCounts() {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final String table : RdbmsEngineStateSchema.PROCESS_INSTANCE_TABLES) {
      counts.put(table, countRows(table));
    }
    return counts;
  }

  private int committedTimesTaken(final long flowScopeKey) throws SQLException {
    try (final var freshConnection = dataSource.getConnection();
        final var statement =
            freshConnection.prepareStatement(
                "SELECT SUM(times_taken) FROM CAM_RU_TAKEN_SEQUENCE_FLOW"
                    + " WHERE partition_id = ? AND flow_scope_key = ?")) {
      statement.setInt(1, partitionId);
      statement.setLong(2, flowScopeKey);
      try (final ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getInt(1);
      }
    }
  }

  /**
   * Records how the connection's transaction is ended, and can fail the next commit like a
   * serialization failure would.
   */
  private static final class ConnectionSpy {

    private static final Set<String> TRANSACTION_METHODS =
        Set.of("commit", "rollback", "close", "setAutoCommit");

    private final List<String> transactionCalls = new ArrayList<>();
    private boolean failNextCommit;

    private Connection proxy(final Connection delegate) {
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                if (TRANSACTION_METHODS.contains(method.getName())) {
                  transactionCalls.add(method.getName());
                }
                if (failNextCommit && method.getName().equals("commit")) {
                  failNextCommit = false;
                  throw new SQLTransientException("injected serialization failure", "40001");
                }
                try {
                  return method.invoke(delegate, args);
                } catch (final InvocationTargetException e) {
                  throw e.getCause();
                }
              });
    }
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
