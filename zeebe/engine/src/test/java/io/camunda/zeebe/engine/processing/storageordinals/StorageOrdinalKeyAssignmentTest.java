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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins the storage ordinal key assignment end to end: with archiverless mode enabled the engine
 * selects the fixed provider, stamps the configured key on the root process instance at creation,
 * and all follow-up records of the instance inherit that key.
 */
public final class StorageOrdinalKeyAssignmentTest {

  private static final int FIXED_KEY = 1234;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinalKey(FIXED_KEY));

  @Test
  public void shouldAssignConfiguredKeyToProcessInstanceRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("pi-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("pi-records-process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinalKey())
        .containsOnly(FIXED_KEY);
  }

  @Test
  public void shouldAssignConfiguredKeyToVariableRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("variable-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("variable-records-process")
            .withVariable("ordinalVariable", 1)
            .create();

    // then
    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("ordinalVariable")
            .getFirst();
    assertThat(variableCreated.getValue().getStorageOrdinalKey()).isEqualTo(FIXED_KEY);
  }

  @Test
  public void shouldAssignConfiguredKeyToJobRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-records-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-records-process").create();

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(jobCreated.getValue().getStorageOrdinalKey()).isEqualTo(FIXED_KEY);
  }

  @Test
  public void shouldAssignConfiguredKeyToUserTaskRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("user-task-records-process")
                .startEvent()
                .userTask("user-task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("user-task-records-process").create();

    // then
    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(userTaskCreated.getValue().getStorageOrdinalKey()).isEqualTo(FIXED_KEY);
  }

  @Test
  public void shouldAssignConfiguredKeyToProcessEventRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-event-records-process")
                .startEvent()
                .intermediateCatchEvent("catch-event")
                .message(m -> m.name("ordinal-message").zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done())
        .deploy();
    engine
        .processInstance()
        .ofBpmnProcessId("process-event-records-process")
        .withVariable("key", "correlation-key-1")
        .create();

    // when
    engine.message().withName("ordinal-message").withCorrelationKey("correlation-key-1").publish();

    // then
    final var records =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_EVENT
                        && r.getIntent() == ProcessEventIntent.TRIGGERING)
            .asList();
    final var processEventTriggering = records.get(records.size() - 1);
    assertThat(((ProcessEventRecordValue) processEventTriggering.getValue()).getStorageOrdinalKey())
        .isEqualTo(FIXED_KEY);
  }

  @Test
  public void shouldAssignConfiguredKeyToProcessInstanceBatchRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("batch-records-process")
                .startEvent()
                .serviceTask(
                    "multi-instance-task",
                    t ->
                        t.zeebeJobType("ordinal-multi-instance")
                            .multiInstance(
                                m ->
                                    m.parallel()
                                        .zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("batch-records-process")
            .withVariable("items", List.of(1, 2, 3))
            .create();

    // then
    final var batchActivate =
        RecordingExporter.processInstanceBatchRecords(ProcessInstanceBatchIntent.ACTIVATE)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(batchActivate.getValue().getStorageOrdinalKey()).isEqualTo(FIXED_KEY);
  }

  @Test
  public void shouldAssignConfiguredKeyToDecisionEvaluationRecords() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess("decision-records-process")
                .startEvent()
                .businessRuleTask(
                    "business-rule-task",
                    t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("decision-records-process")
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluated.getValue().getStorageOrdinalKey()).isEqualTo(FIXED_KEY);
  }
}
