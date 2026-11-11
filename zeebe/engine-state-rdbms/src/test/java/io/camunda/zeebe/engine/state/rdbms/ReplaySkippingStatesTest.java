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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.EventScopeInstanceStateImpl;
import io.camunda.zeebe.engine.state.instance.JobStateImpl;
import io.camunda.zeebe.engine.state.jobmetrics.JobMetricsStateImpl;
import io.camunda.zeebe.engine.state.metrics.UsageMetricStateImpl;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.variable.VariableStateImpl;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.time.InstantSource;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * After a restart, the replay applies events whose changes the database already contains, while the
 * rows are in the later state of the database's position: a job activated and completed after the
 * last snapshot is gone, and updating it again would fail. The relational states leave the rows
 * alone for these events, and behave as before for all others.
 */
final class ReplaySkippingStatesTest {

  /** The position of the last command whose changes the database contains. */
  private static final long DATABASE_POSITION = 10;

  private static final String TENANT_ID = "replay-tenant";

  private static EngineStateDatabase database;

  private final QueryCounter queryCounter = new QueryCounter();
  private int partitionId;
  private JdbcTransactionContext context;
  private MutableElementInstanceState elementInstanceState;
  private MutableVariableState variableState;
  private MutableJobState jobState;
  private MutableEventScopeInstanceState eventScopeState;
  private MutableUsageMetricState usageMetricState;
  private MutableJobMetricsState jobMetricsState;

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
    database.observed().decorate(queryCounter::wrap);
    partitionId = database.nextPartitionId();
    context = new JdbcTransactionContext(DATABASE_POSITION);
    final var session = database.entitySession(context, partitionId);
    variableState =
        new ReplaySkippingVariableState(
            new VariableStateImpl(new RdbmsVariableStore(session)), context);
    elementInstanceState =
        new ReplaySkippingElementInstanceState(
            new ElementInstanceStateImpl(new RdbmsElementInstanceStore(session), variableState),
            context);
    jobState = new ReplaySkippingJobState(new JobStateImpl(new RdbmsJobStore(session)), context);
    eventScopeState =
        new ReplaySkippingEventScopeInstanceState(
            new EventScopeInstanceStateImpl(new RdbmsEventScopeStore(session)), context);
    usageMetricState =
        new ReplaySkippingUsageMetricState(
            new UsageMetricStateImpl(new RdbmsUsageMetricStore(session)), context);
    jobMetricsState =
        new ReplaySkippingJobMetricsState(
            new JobMetricsStateImpl(
                new RdbmsJobMetricsStore(session),
                InstantSource.system(),
                new EngineConfiguration()),
            context);
  }

  @AfterEach
  void tearDown() {
    database.observed().reset();
  }

  @Test
  void shouldNeitherReadNorWriteRowsWhenReplayingCommittedChanges() throws Exception {
    // given - the job and its element instance were completed before the restart: no rows left
    final var transaction = context.getCurrentTransaction();
    context.markAsReplaying(DATABASE_POSITION);
    final var job = jobRecord();

    // when - the events of the job's activation and completion are replayed
    final List<String> queries =
        queryCounter.queriesOf(
            () -> {
              jobState.activate(1, job);
              jobState.complete(1, job);
              jobMetricsState.incrementMetric(job, JobMetricsExportState.COMPLETED);
              elementInstanceState.updateInstance(2, instance -> instance.setJobKey(-1));
              elementInstanceState.removeInstance(2);
              variableState.setVariableLocal(
                  3, 2, 4, wrapString("x"), MsgPackUtil.asMsgPack("{'x':1}"));
              eventScopeState.triggerEvent(
                  2, 5, wrapString("event"), MsgPackUtil.asMsgPack("{}"), 2);
              eventScopeState.deleteInstance(2);
              usageMetricState.recordRPIMetric(TENANT_ID);
            });
    transaction.commit();

    // then
    assertThat(queries).isEmpty();
    for (final var table :
        List.of(
            "CAM_RU_JOB",
            "CAM_RU_JOB_METRIC",
            "CAM_RU_ELEMENT_INSTANCE",
            "CAM_RU_VARIABLE",
            "CAM_RU_EVENT_SCOPE",
            "CAM_RU_EVENT_TRIGGER",
            "CAM_RU_USAGE_METRIC")) {
      assertThat(database.countRows(table, partitionId, "TRUE")).describedAs(table).isZero();
    }
  }

  @Test
  void shouldReturnWhatTheAppliersUseWithoutWritingWhenReplayingCommittedChanges()
      throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    context.markAsReplaying(DATABASE_POSITION);
    final var parent =
        new ElementInstance(
            100, ProcessInstanceIntent.ELEMENT_ACTIVATED, record(BpmnElementType.PROCESS));

    // when
    final List<String> queries =
        queryCounter.queriesOf(
            () -> {
              final var child =
                  elementInstanceState.newInstance(
                      parent,
                      101,
                      record(BpmnElementType.SERVICE_TASK),
                      ProcessInstanceIntent.ELEMENT_ACTIVATING);

              // then - the instance the delegate would have stored
              assertThat(child.getKey()).isEqualTo(101);
              assertThat(child.getParentKey()).isEqualTo(100);
              assertThat(child.getState()).isEqualTo(ProcessInstanceIntent.ELEMENT_ACTIVATING);
              assertThat(child.getValue().getBpmnElementType())
                  .isEqualTo(BpmnElementType.SERVICE_TASK);
              assertThat(parent.getNumberOfActiveElementInstances()).isOne();

              assertThat(jobState.updateJobRetries(1, 3)).isNull();
              assertThat(eventScopeState.pollEventTrigger(100)).isNull();
            });
    transaction.commit();

    // then
    assertThat(queries).isEmpty();
    assertThat(database.countRows("CAM_RU_ELEMENT_INSTANCE", partitionId, "TRUE")).isZero();
  }

  @Test
  void shouldReadARemovedElementInstanceAsDetachedWhenReplayingCommittedChanges() throws Exception {
    // given - the flow scope of a replayed event was removed before the restart
    final var transaction = context.getCurrentTransaction();
    context.markAsReplaying(DATABASE_POSITION);

    // when
    final var removed = elementInstanceState.getInstance(100);
    final var noInstance = elementInstanceState.getInstance(-1);
    removed.incrementActiveSequenceFlows();
    elementInstanceState.updateInstance(removed);
    transaction.commit();

    // then
    assertThat(removed.getKey()).isEqualTo(100);
    assertThat(noInstance).isNull();
    assertThat(database.countRows("CAM_RU_ELEMENT_INSTANCE", partitionId, "TRUE")).isZero();
  }

  @Test
  void shouldDelegateWhenReplayingChangesAfterTheDatabasePosition() throws Exception {
    // given
    final var transaction = context.getCurrentTransaction();
    context.markAsReplaying(DATABASE_POSITION + 1);

    // when
    elementInstanceState.newInstance(
        100, record(BpmnElementType.PROCESS), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    final var missing = elementInstanceState.getInstance(200);
    transaction.commit();

    // then
    assertThat(missing).isNull();
    assertThat(database.countRows("CAM_RU_ELEMENT_INSTANCE", partitionId, "TRUE")).isOne();
  }

  @Test
  void shouldDelegateWhenProcessing() throws Exception {
    // given - the stream processor processes a command, without replaying
    final var transaction = context.getCurrentTransaction();

    // when
    elementInstanceState.newInstance(
        100, record(BpmnElementType.PROCESS), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    jobState.insertJobRecordActivatable(
        1, jobRecord().setElementInstanceKey(100).setProcessInstanceKey(100));
    transaction.commit();

    // then
    assertThat(database.countRows("CAM_RU_ELEMENT_INSTANCE", partitionId, "TRUE")).isOne();
    assertThat(database.countRows("CAM_RU_JOB", partitionId, "TRUE")).isOne();
  }

  private static JobRecord jobRecord() {
    return new JobRecord().setType("replay").setRetries(1).setTenantId(TENANT_ID);
  }

  private static ProcessInstanceRecord record(final BpmnElementType type) {
    return new ProcessInstanceRecord()
        .setElementId(type.name())
        .setBpmnProcessId(wrapString("process"))
        .setProcessInstanceKey(100)
        .setFlowScopeKey(type == BpmnElementType.PROCESS ? -1 : 100)
        .setVersion(1)
        .setProcessDefinitionKey(2)
        .setTenantId(TENANT_ID)
        .setBpmnElementType(type);
  }
}
