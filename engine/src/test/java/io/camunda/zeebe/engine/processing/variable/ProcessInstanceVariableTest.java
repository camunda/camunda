/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessInstanceVariableTest {

  public static final String PROCESS_ID = "process";
  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();
  private static long processDefinitionKey;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    processDefinitionKey =
        ENGINE_RULE
            .deployment()
            .withXmlResource(PROCESS)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
  }

  @Test
  public void shouldCreateVariableByProcessInstanceCreation() {
    // given

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables("{'x':1}").create();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByJobCompletion() {
    // given
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariables("{'x':1}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByOutputMapping() {
    // given
    final long processDefinitionKey =
        ENGINE_RULE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("shouldCreateVariableByOutputMapping")
                    .startEvent()
                    .serviceTask(
                        "task", t -> t.zeebeJobType("test").zeebeOutputExpression("x", "y"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId("shouldCreateVariableByOutputMapping")
            .create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariables("{'x':1}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("y")
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasName("y")
        .hasValue("1");
  }

  @Test
  public void shouldCreateVariableByUpdateVariables() {
    // given
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Maps.of(entry("x", 1)))
        .update();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasName("x")
        .hasValue("1");
  }

  @Test
  public void shouldCreateMultipleVariables() {
    // given

    // when
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1, 'y':2}")
            .create();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(2)
        .contains(tuple("x", "1"), tuple("y", "2"));
  }

  @Test
  public void shouldUpdateVariableByJobCompletion() {
    // given
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables("{'x':1}").create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariables("{'x':2}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasName("x")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateVariableByOutputMapping() {
    // given
    final long processDefinitionKey =
        ENGINE_RULE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("shouldUpdateVariableByOutputMapping")
                    .startEvent()
                    .serviceTask(
                        "task", t -> t.zeebeJobType("test").zeebeOutputExpression("x", "y"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId("shouldUpdateVariableByOutputMapping")
            .withVariables("{'y':1}")
            .create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariables("{'x':2}")
        .complete();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasName("y")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateVariableByUpdateVariables() {
    // given
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables("{'x':1}").create();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Maps.of(entry("x", 2)))
        .update();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasName("x")
        .hasValue("2");
  }

  @Test
  public void shouldUpdateMultipleVariables() {
    // given
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'x':1, 'y':2, 'z':3}")
            .create();

    // when
    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withVariables("{'x':1, 'y':4, 'z':5}")
        .complete();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withIntent(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(2)
        .contains(tuple("y", "4"), tuple("z", "5"));
  }

  @Test
  public void shouldCreateAndUpdateVariables() {
    // given
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables("{'x':1}").create();

    final Record<VariableRecordValue> variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Maps.of(entry("x", 2), entry("y", 3)))
        .update();

    // then
    assertThat(
            RecordingExporter.variableRecords()
                .skipUntil(r -> r.getPosition() > variableCreated.getPosition())
                .limit(2))
        .extracting(
            record ->
                tuple(
                    record.getIntent(),
                    record.getValue().getProcessDefinitionKey(),
                    record.getValue().getName(),
                    record.getValue().getValue()))
        .hasSize(2)
        .contains(
            tuple(VariableIntent.UPDATED, processDefinitionKey, "x", "2"),
            tuple(VariableIntent.CREATED, processDefinitionKey, "y", "3"));
  }

  @Test
  public void shouldHaveSameKeyOnVariableUpdate() {
    // given
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariables("{'x':1}").create();

    final Record<VariableRecordValue> variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE_RULE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Maps.of(entry("x", 2)))
        .update();

    // then
    final Record<VariableRecordValue> variableUpdated =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(variableCreated.getKey()).isEqualTo(variableUpdated.getKey());
  }
}
