/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ProcessInstanceVariableTypeTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String variables;

  @Parameter(1)
  public String expectedValue;

  @Parameters(name = "with variables: {0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"{'x':'foo'}", "\"foo\""},
      {"{'x':123}", "123"},
      {"{'x':true}", "true"},
      {"{'x':false}", "false"},
      {"{'x':null}", "null"},
      {"{'x':[1,2,3]}", "[1,2,3]"},
      {"{'x':{'y':123}}", "{\"y\":123}"},
    };
  }

  @BeforeClass
  public static void deployProcess() {
    ENGINE_RULE.deployment().withXmlResource(PROCESS).deploy();
  }

  @Test
  public void shouldWriteVariableCreatedEvent() {
    // when
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(variables)
            .create();

    // then
    final Record<VariableRecordValue> variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final VariableRecordValue value = variableRecord.getValue();
    assertThat(value.getScopeKey()).isEqualTo(processInstanceKey);
    assertThat(value.getName()).isEqualTo("x");
    assertThat(value.getValue()).isEqualTo(expectedValue);
  }
}
