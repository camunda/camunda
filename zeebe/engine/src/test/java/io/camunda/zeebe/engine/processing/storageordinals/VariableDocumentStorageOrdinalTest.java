/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class VariableDocumentStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 7;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignInstanceOrdinalToUpdatedEvent() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("variable-document-updated-process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("vd-ordinal-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("variable-document-updated-process").create();

    // when
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("foo", "bar")).update();

    // then
    final var updated =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED)
            .withScopeKey(processInstanceKey)
            .getFirst();
    assertThat(updated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignInstanceOrdinalToUserTaskUpdatingAndUpdatedEvents() {
    // given
    final var userTaskActivated = createUserTaskWithUpdatingListener();
    final long userTaskElementInstanceKey = userTaskActivated.getKey();
    final long processInstanceKey = userTaskActivated.getValue().getProcessInstanceKey();

    // when
    engine
        .variables()
        .ofScope(userTaskElementInstanceKey)
        .withDocument(Map.of("foo", "bar"))
        .expectUpdating()
        .update();
    engine.job().ofInstance(processInstanceKey).withType("vd-ordinal-updating-listener").complete();

    // then
    final var updating =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATING)
            .withScopeKey(userTaskElementInstanceKey)
            .getFirst();
    assertThat(updating.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    final var updated =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED)
            .withScopeKey(userTaskElementInstanceKey)
            .getFirst();
    assertThat(updated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignInstanceOrdinalToUserTaskUpdateDeniedEvent() {
    // given
    final var userTaskActivated = createUserTaskWithUpdatingListener();
    final long userTaskElementInstanceKey = userTaskActivated.getKey();
    final long processInstanceKey = userTaskActivated.getValue().getProcessInstanceKey();
    engine
        .variables()
        .ofScope(userTaskElementInstanceKey)
        .withDocument(Map.of("foo", "bar"))
        .expectUpdating()
        .update();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("vd-ordinal-updating-listener")
        .withResult(new JobResult().setDenied(true))
        .complete();

    // then
    final var denied =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATE_DENIED)
            .withScopeKey(userTaskElementInstanceKey)
            .getFirst();
    assertThat(denied.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldMarkNotFoundRejectionAsNotOrdinalControlled() {
    // given
    final long missingScopeKey = 123456789L;

    // when
    final var rejection =
        engine
            .variables()
            .ofScope(missingScopeKey)
            .withDocument(Map.of("foo", "bar"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getValue().getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  public void shouldAssignInstanceOrdinalToInvalidStateRejection() {
    // given
    final long userTaskElementInstanceKey = createUserTaskWithUpdatingListener().getKey();
    engine
        .variables()
        .ofScope(userTaskElementInstanceKey)
        .withDocument(Map.of("foo", "bar"))
        .expectUpdating()
        .update();

    // when
    final var rejection =
        engine
            .variables()
            .ofScope(userTaskElementInstanceKey)
            .withDocument(Map.of("foo", "baz"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private Record<ProcessInstanceRecordValue> createUserTaskWithUpdatingListener() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("variable-document-user-task-process")
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .zeebeTaskListener(l -> l.updating().type("vd-ordinal-updating-listener"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("variable-document-user-task-process").create();

    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .getFirst();
  }
}
