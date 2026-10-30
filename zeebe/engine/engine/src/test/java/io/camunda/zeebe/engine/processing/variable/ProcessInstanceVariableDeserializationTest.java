/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * To ensure that we can serialize and deserialize all FEEL data types into process instance
 * variables (i.e., a working round-trip).
 */
@RunWith(Parameterized.class)
public final class ProcessInstanceVariableDeserializationTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  private static final String VARIABLE_SERIALIZED = "value";
  private static final String VARIABLE_IS_EQUAL = "isEqual";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String variableValue;

  @Parameter(1)
  public String variableConversionExpression;

  // We use a process with two script tasks:
  // 1. to serialize the variable value into a process variable
  // 2. to convert the serialized variable back into the original type and compare it
  private static BpmnModelInstance process(
      final String variableValue, final String variableConversionExpression) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .scriptTask(
            "serialize",
            t -> t.zeebeExpression(variableValue).zeebeResultVariable(VARIABLE_SERIALIZED))
        .scriptTask(
            "assert",
            t ->
                t.zeebeExpression("%s = %s".formatted(variableConversionExpression, variableValue))
                    .zeebeResultVariable(VARIABLE_IS_EQUAL))
        .done();
  }

  // A tuple of FEEL expressions:
  // 1. to create the variable value
  // 2. to convert the serialized variable back into the original type.
  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      // primitive types
      {"\"Hello Zee!\"", VARIABLE_SERIALIZED},
      {"42", VARIABLE_SERIALIZED},
      {"3.14159", VARIABLE_SERIALIZED},
      {"true", VARIABLE_SERIALIZED},
      {"false", VARIABLE_SERIALIZED},
      // complex types
      {"[1,2,3]", VARIABLE_SERIALIZED},
      {"{name: \"Zee\", age: 3}", VARIABLE_SERIALIZED},
      // temporal types
      {
        "date and time(\"2025-11-21T15:00:00\")", "date and time(%s)".formatted(VARIABLE_SERIALIZED)
      },
      {
        "date and time(\"2025-11-21T15:00:00Z\")",
        "date and time(%s)".formatted(VARIABLE_SERIALIZED)
      },
      {
        "date and time(\"2025-11-21T15:00:00+01:00\")",
        "date and time(%s)".formatted(VARIABLE_SERIALIZED)
      },
      {
        "date and time(\"2025-11-21T15:00:00@Europe/Berlin\")",
        "date and time(%s)".formatted(VARIABLE_SERIALIZED)
      },
      {"date(\"2025-11-21\")", "date(%s)".formatted(VARIABLE_SERIALIZED)},
      {"time(\"15:00:00\")", "time(%s)".formatted(VARIABLE_SERIALIZED)},
      {"time(\"15:00:00Z\")", "time(%s)".formatted(VARIABLE_SERIALIZED)},
      {"time(\"15:00:00+01:00\")", "time(%s)".formatted(VARIABLE_SERIALIZED)},
      {"time(\"15:00:00@Europe/Berlin\")", "time(%s)".formatted(VARIABLE_SERIALIZED)},
      {"duration(\"PT8H\")", "duration(%s)".formatted(VARIABLE_SERIALIZED)},
      {"duration(\"P10Y\")", "duration(%s)".formatted(VARIABLE_SERIALIZED)},
    };
  }

  @Test
  public void shouldDeserializeValue() {
    // given
    final var process = process(variableValue, variableConversionExpression);
    ENGINE_RULE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(getVariableValue(processInstanceKey, VARIABLE_SERIALIZED))
        .describedAs("Ensure the variable is serialized correctly")
        .isNotEqualTo("null");

    assertThat(getVariableValue(processInstanceKey, VARIABLE_IS_EQUAL))
        .describedAs("The deserialized variable should be equal to the original value")
        .isEqualTo("true");
  }

  private static String getVariableValue(final long processInstanceKey, final String variableName) {
    return RecordingExporter.variableRecords(VariableIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withName(variableName)
        .getFirst()
        .getValue()
        .getValue();
  }
}
