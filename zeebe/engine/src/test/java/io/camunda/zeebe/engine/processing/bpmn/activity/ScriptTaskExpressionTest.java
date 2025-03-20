/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ScriptTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ScriptTaskExpressionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String RESULT_VARIABLE = "result";
  private static final String OUTPUT_TARGET = "output";

  private static final String A_STRING = "foobar";
  private static final String A_SUB_STRING = "\"bar\"";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithScriptTask(
      final Consumer<ScriptTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .scriptTask(TASK_ID, modifier)
        .endEvent()
        .done();
  }

  @Test
  public void shouldUseCorrectlyPutContextVariables() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .scriptTask(
                TASK_ID,
                t ->
                    t.zeebeExpression(
                            "=context put(mar2_links, [key_link, \"str_pe_fsz_b\"], \"test\")")
                        .zeebeResultVariable(RESULT_VARIABLE))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of(
                    "key_link",
                    "4E3_5001",
                    "mar2_links",
                    JsonUtil.fromJsonAsMap(
                        """
            {
               "4E2_5030": {
                  "str_sd_port_a": "0/0/0/0",
                  "str_sd_fsz_b": "7ZG0",
                  "str_pe_fsz_b": "7ZG0"
               },
               "4E3_5001": {
                  "str_sd_port_a": "0/0/0/0",
                  "str_sd_fsz_b": "7ZP1",
                  "str_pe_fsz_b": "7ZN1"
               }
            }""")))
            .create();

    // then
    final var actualValue =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName(RESULT_VARIABLE)
            .getFirst()
            .getValue()
            .getValue();
    JsonUtil.assertEquality(
        actualValue,
        """
                {
                  "4E2_5030": {
                    "str_sd_port_a": "0/0/0/0",
                    "str_sd_fsz_b": "7ZG0",
                    "str_pe_fsz_b": "7ZG0"
                  },
                  "4E3_5001": {
                    "str_sd_port_a": "0/0/0/0",
                    "str_sd_fsz_b": "7ZP1",
                    "str_pe_fsz_b": "test"
                  }
                }
                """);
  }

  @Test
  public void shouldActivateTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t -> t.zeebeExpression("substring(x, 4)").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", A_STRING).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SCRIPT_TASK)
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SCRIPT_TASK)
            .getFirst();

    Assertions.assertThat(taskActivating.getValue())
        .hasElementId(TASK_ID)
        .hasBpmnElementType(BpmnElementType.SCRIPT_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldActivateTaskWithCustomTenant() {
    // given
    final String tenantId = "foo";
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t -> t.zeebeExpression("substring(x, 4)").zeebeResultVariable(RESULT_VARIABLE)))
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("x", A_STRING)
            .withTenantId(tenantId)
            .create();

    // then
    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SCRIPT_TASK)
            .withTenantId(tenantId)
            .getFirst();

    Assertions.assertThat(taskActivating.getValue())
        .hasElementId(TASK_ID)
        .hasBpmnElementType(BpmnElementType.SCRIPT_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(tenantId);
  }

  @Test
  public void shouldCompleteTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t -> t.zeebeExpression("substring(x, 4)").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", A_STRING).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SCRIPT_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SCRIPT_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteTaskWithCustomTenant() {
    // given
    final String tenantId = "foo";
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t -> t.zeebeExpression("substring(x, 4)").zeebeResultVariable(RESULT_VARIABLE)))
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("x", A_STRING)
            .withTenantId(tenantId)
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withTenantId(tenantId)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SCRIPT_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SCRIPT_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldWriteResultAsProcessInstanceVariable() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t -> t.zeebeExpression("substring(x, 4)").zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", A_STRING).create();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(RESULT_VARIABLE)
                .getFirst())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getScopeKey, VariableRecordValue::getValue)
        .containsExactly(processInstanceKey, A_SUB_STRING);
  }

  @Test
  public void shouldUseResultInOutputMappings() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t ->
                    t.zeebeExpression("substring(x, 4)")
                        .zeebeResultVariable(RESULT_VARIABLE)
                        .zeebeOutputExpression(RESULT_VARIABLE, OUTPUT_TARGET)))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", A_STRING).create();

    final long taskInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SCRIPT_TASK)
            .getFirst()
            .getKey();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(RESULT_VARIABLE)
                .getFirst())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getScopeKey, VariableRecordValue::getValue)
        .containsExactly(taskInstanceKey, A_SUB_STRING);

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(OUTPUT_TARGET)
                .getFirst())
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getScopeKey, VariableRecordValue::getValue)
        .containsExactly(processInstanceKey, A_SUB_STRING);
  }

  @Test
  public void shouldCreateIncidentIfScriptExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t ->
                    t.zeebeExpression("assert(x, x != null)")
                        .zeebeResultVariable(RESULT_VARIABLE)
                        .zeebeOutputExpression(RESULT_VARIABLE, OUTPUT_TARGET)))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final var scriptTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ID)
            .withElementType(BpmnElementType.SCRIPT_TASK)
            .getFirst();

    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Assertion failure on evaluate the expression 'assert(x, x != null)': \
            The condition is not fulfilled \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'x'
            [NO_VARIABLE_FOUND] No variable found with name 'x'
            [ASSERT_FAILURE] The condition is not fulfilled""")
        .hasBpmnProcessId(scriptTask.getValue().getBpmnProcessId())
        .hasProcessDefinitionKey(scriptTask.getValue().getProcessDefinitionKey())
        .hasProcessInstanceKey(scriptTask.getValue().getProcessInstanceKey())
        .hasElementId(scriptTask.getValue().getElementId())
        .hasElementInstanceKey(scriptTask.getKey())
        .hasVariableScopeKey(scriptTask.getKey())
        .hasJobKey(-1);
  }

  @Test
  public void shouldResolveIncidentIfScriptExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithScriptTask(
                t ->
                    t.zeebeExpression("assert(x, x != null)")
                        .zeebeResultVariable(RESULT_VARIABLE)
                        .zeebeOutputExpression(RESULT_VARIABLE, OUTPUT_TARGET)))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentCreatedRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE
        .variables()
        .ofScope(incidentCreatedRecord.getValue().getElementInstanceKey())
        .withDocument(Map.of("x", A_STRING))
        .update();

    // when
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreatedRecord.getKey())
            .resolve();

    // then
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName(OUTPUT_TARGET)
                .getFirst()
                .getValue())
        .hasValue("\"foobar\"");

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedRecord.getKey());
  }
}
