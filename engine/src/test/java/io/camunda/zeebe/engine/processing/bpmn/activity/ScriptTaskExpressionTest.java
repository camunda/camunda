/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.engine.processing.variable.mapping.VariableValue.variable;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.variable.mapping.VariableValue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.ScriptTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ScriptTaskExpressionTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  private static final String A_TIME_VALUE = "\"04:20:00@Europe/Berlin\"";
  private static final String A_LOCAL_TIME_VALUE = "\"04:20\"";
  private static final String A_DATE_VALUE = "\"2021-02-24\"";
  private static final String A_LOCAL_DATE_AND_TIME_VALUE = "\"2021-02-24T04:20\"";
  private static final String A_DATE_AND_TIME_VALUE = "\"2021-02-24T04:20+01:00\"";
  private static final String A_DAY_TIME_DURATION_VALUE = "\"PT42H56M33S\"";
  private static final String A_YEAR_MONTH_DURATION_VALUE = "\"P2Y3M\"";
  private static final String A_STRING = "\"foobar\"";
  private static final String A_SUB_STRING = "\"bar\"";
  private static final String A_UPPER_STRING = "\"FOOBAR\"";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String initialVariables;

  @Parameter(1)
  public Consumer<ScriptTaskBuilder> mappings;

  @Parameter(2)
  public List<VariableValue> expectedScopeVariables;

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "{'x': 1}",
        mapping(b -> b.zeebeExpression("x").zeebeResultVariable("y")),
        scopeVariables(variable("y", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeExpression("decimal(x / 3, 2)").zeebeResultVariable("y")),
        scopeVariables(variable("y", "0.33"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeExpression("y").zeebeResultVariable("z")),
        scopeVariables(variable("z", "2"))
      },
      {
        "{'x': 1}",
        mapping(
            b -> b.zeebeInputExpression("x", "y").zeebeExpression("y").zeebeResultVariable("z")),
        scopeVariables(variable("z", "1"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeExpression("x").zeebeResultVariable("z")),
        scopeVariables(variable("z", "{\"y\":2}"))
      },
      {
        "{'z': {'x': 1}, 'y': 2}",
        mapping(
            b ->
                b.zeebeOutputExpression("y", "z.y")
                    .zeebeExpression("z.x")
                    .zeebeResultVariable("result")),
        scopeVariables(variable("z", "{\"x\":1,\"y\":2}"), variable("result", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(
            b ->
                b.zeebeInputExpression("x", "z.x")
                    .zeebeInputExpression("y", "z.y")
                    .zeebeExpression("z")
                    .zeebeResultVariable("result")),
        scopeVariables(variable("result", "{\"x\":1,\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeExpression("x.y").zeebeResultVariable("z")),
        scopeVariables(variable("z", "2"))
      },
      {
        "{'x': %s}".formatted(A_STRING),
        mapping(b -> b.zeebeExpression("contains(x, \"of\")").zeebeResultVariable("y")),
        scopeVariables(variable("y", "false"))
      },
      {
        "{'x': %s}".formatted(A_STRING),
        mapping(b -> b.zeebeExpression("starts with(x, \"fo\")").zeebeResultVariable("y")),
        scopeVariables(variable("y", "true"))
      },
      {
        "{'x': %s}".formatted(A_STRING),
        mapping(b -> b.zeebeExpression("substring(x, 4)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_SUB_STRING))
      },
      {
        "{'x': %s}".formatted(A_STRING),
        mapping(b -> b.zeebeExpression("upper case(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_UPPER_STRING))
      },
      {
        "{'x': %s}".formatted(A_TIME_VALUE),
        mapping(b -> b.zeebeExpression("time(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_LOCAL_TIME_VALUE),
        mapping(b -> b.zeebeExpression("time(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_LOCAL_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_DATE_VALUE),
        mapping(b -> b.zeebeExpression("date(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_DATE_VALUE))
      },
      {
        "{'x': %s}".formatted(A_LOCAL_DATE_AND_TIME_VALUE),
        mapping(b -> b.zeebeExpression("date and time(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_LOCAL_DATE_AND_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_DATE_AND_TIME_VALUE),
        mapping(b -> b.zeebeExpression("date and time(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_DATE_AND_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_DAY_TIME_DURATION_VALUE),
        mapping(b -> b.zeebeExpression("duration(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_DAY_TIME_DURATION_VALUE))
      },
      {
        "{'x': %s}".formatted(A_YEAR_MONTH_DURATION_VALUE),
        mapping(b -> b.zeebeExpression("duration(x)").zeebeResultVariable("y")),
        scopeVariables(variable("y", A_YEAR_MONTH_DURATION_VALUE))
      }
    };
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .scriptTask(
                    "scriptTask",
                    builder -> {
                      mappings.accept(builder);
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(initialVariables)
            .create();

    // then
    final long scriptTaskActivatePosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("scriptTask")
            .limit(1)
            .getFirst()
            .getPosition();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > scriptTaskActivatePosition)
                .withScopeKey(processInstanceKey)
                .limit(expectedScopeVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .hasSameSizeAs(expectedScopeVariables)
        .containsAll(expectedScopeVariables);
  }

  private static Consumer<ScriptTaskBuilder> mapping(
      final Consumer<ScriptTaskBuilder> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<VariableValue> scopeVariables(final VariableValue... variables) {
    return Arrays.asList(variables);
  }
}
