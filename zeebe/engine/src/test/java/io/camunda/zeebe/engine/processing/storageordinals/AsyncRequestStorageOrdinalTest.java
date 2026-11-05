/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that the ASYNC_REQUEST records of a request carry the storage ordinal of the process
 * instance that owns the scope, for both the RECEIVED event and the PROCESSED event that replays
 * the stored record.
 */
public final class AsyncRequestStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 7;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignInstanceOrdinalToCancelRequestRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("async-request-cancel-process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("async-request-cancel-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("async-request-cancel-process").create();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.asyncRequestRecords()
                .withRequestScopeKey(processInstanceKey)
                .withRequestValueType(ValueType.PROCESS_INSTANCE)
                .withRequestIntent(ProcessInstanceIntent.CANCEL)
                .limit(2))
        .extracting(Record::getIntent, record -> record.getValue().getStorageOrdinal())
        .containsExactly(
            tuple(AsyncRequestIntent.RECEIVED, FIXED_ORDINAL),
            tuple(AsyncRequestIntent.PROCESSED, FIXED_ORDINAL));
  }

  @Test
  public void shouldAssignInstanceOrdinalToResumeRequestRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("async-request-resume-process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("async-request-resume-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("async-request-resume-process").create();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    assertThat(
            RecordingExporter.asyncRequestRecords()
                .withRequestScopeKey(processInstanceKey)
                .withRequestValueType(ValueType.PROCESS_INSTANCE)
                .withRequestIntent(ProcessInstanceIntent.RESUME)
                .limit(2))
        .extracting(Record::getIntent, record -> record.getValue().getStorageOrdinal())
        .containsExactly(
            tuple(AsyncRequestIntent.RECEIVED, FIXED_ORDINAL),
            tuple(AsyncRequestIntent.PROCESSED, FIXED_ORDINAL));
  }

  @Test
  public void shouldAssignInstanceOrdinalToUserTaskCompleteRequestRecords() {
    // given: a completing task listener keeps the request open until the listener job completes
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("async-request-user-task-process")
                .startEvent()
                .userTask(
                    "task",
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(
                                l -> l.completing().type("async-request-complete-listener")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("async-request-user-task-process").create();
    final long userTaskElementInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    engine.userTask().ofInstance(processInstanceKey).complete();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("async-request-complete-listener")
        .complete();

    // then
    assertThat(
            RecordingExporter.asyncRequestRecords()
                .withRequestScopeKey(userTaskElementInstanceKey)
                .withRequestValueType(ValueType.USER_TASK)
                .withRequestIntent(UserTaskIntent.COMPLETE)
                .limit(2))
        .extracting(Record::getIntent, record -> record.getValue().getStorageOrdinal())
        .containsExactly(
            tuple(AsyncRequestIntent.RECEIVED, FIXED_ORDINAL),
            tuple(AsyncRequestIntent.PROCESSED, FIXED_ORDINAL));
  }

  @Test
  public void shouldKeepPersistedScopeOrdinalOnUserTaskCompleteRequestRecords() {
    // given: a user task persisted with ordinal 0, as an instance created before ordinal support
    engine.stop();
    engine.withEngineConfig(c -> c.setFixedStorageOrdinal(StorageOrdinalRelated.MAIN_INDEX));
    engine.start();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("async-request-persisted-ordinal-process")
                .startEvent()
                .userTask(
                    "task",
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(
                                l -> l.completing().type("async-request-persisted-listener")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("async-request-persisted-ordinal-process")
            .create();
    final long userTaskElementInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // and: the engine now assigns a different ordinal to new instances
    engine.stop();
    engine.withEngineConfig(c -> c.setFixedStorageOrdinal(FIXED_ORDINAL));
    engine.start();

    // when
    engine.userTask().ofInstance(processInstanceKey).complete();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("async-request-persisted-listener")
        .complete();

    // then: both records keep the persisted ordinal of the task, not the current provider value
    assertThat(
            RecordingExporter.asyncRequestRecords()
                .withRequestScopeKey(userTaskElementInstanceKey)
                .withRequestValueType(ValueType.USER_TASK)
                .withRequestIntent(UserTaskIntent.COMPLETE)
                .limit(2))
        .extracting(Record::getIntent, record -> record.getValue().getStorageOrdinal())
        .containsExactly(
            tuple(AsyncRequestIntent.RECEIVED, StorageOrdinalRelated.MAIN_INDEX),
            tuple(AsyncRequestIntent.PROCESSED, StorageOrdinalRelated.MAIN_INDEX));
  }

  @Test
  public void shouldAssignInstanceOrdinalToVariableDocumentRequestRecords() {
    // given: an updating task listener keeps the request open until the listener job completes
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("async-request-variable-process")
                .startEvent()
                .userTask(
                    "task",
                    t ->
                        t.zeebeUserTask()
                            .zeebeTaskListener(
                                l -> l.updating().type("async-request-update-listener")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("async-request-variable-process").create();
    final long userTaskElementInstanceKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    engine
        .variables()
        .ofScope(userTaskElementInstanceKey)
        .withDocument(Map.of("async-request-variable", 1))
        .expectUpdating()
        .update();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("async-request-update-listener")
        .complete();

    // then
    assertThat(
            RecordingExporter.asyncRequestRecords()
                .withRequestScopeKey(userTaskElementInstanceKey)
                .withRequestValueType(ValueType.VARIABLE_DOCUMENT)
                .withRequestIntent(VariableDocumentIntent.UPDATE)
                .limit(2))
        .extracting(Record::getIntent, record -> record.getValue().getStorageOrdinal())
        .containsExactly(
            tuple(AsyncRequestIntent.RECEIVED, FIXED_ORDINAL),
            tuple(AsyncRequestIntent.PROCESSED, FIXED_ORDINAL));
  }
}
