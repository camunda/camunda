/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class NoneEndEventOutputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateVariableForNoneEndEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .zeebeOutputExpression("1", "x")
            .manualTask()
            .endEvent("end")
            .zeebeOutputExpression("x+1", "y")
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE_RULE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("y")
            .getFirst();

    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasName("y")
        .hasValue("2");
  }

  @Test
  public void shouldCreateVariableForSubProcessNoneEndEvent() {
    // given
    final BpmnModelInstance processDefinition =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .zeebeOutputExpression("1", "x")
            .subProcess(
                "sub",
                b -> {
                  b.embeddedSubProcess()
                      .startEvent()
                      .zeebeOutputExpression("x + 1", "sub_x")
                      .endEvent()
                      .zeebeOutputExpression("sub_x + 1", "sub_y");
                })
            .endEvent()
            .zeebeOutputExpression("sub_y + 1", "y")
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE_RULE.deployment().withXmlResource(processDefinition).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("y")
            .getFirst();

    Assertions.assertThat(variableRecord.getValue())
        .hasScopeKey(processInstanceKey)
        .hasName("y")
        .hasValue("4");
  }
}
