/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.variable.mapping;

import static io.zeebe.engine.processing.variable.mapping.VariableValue.variable;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
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
public final class ActivityInputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String initialVariables;

  @Parameter(1)
  public Consumer<SubProcessBuilder> mappings;

  @Parameter(2)
  public List<VariableValue> expectedActivityVariables;

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("x", "x")),
        activityVariables(variable("x", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("x", "y")),
        activityVariables(variable("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeInputExpression("y", "z")),
        activityVariables(variable("z", "2"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeInputExpression("x", "x")),
        activityVariables(variable("x", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeInputExpression("x.y", "y")),
        activityVariables(variable("y", "2"))
      },
    };
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "sub",
                    b -> {
                      b.embeddedSubProcess().startEvent().endEvent();

                      mappings.accept(b);
                    })
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getDeployedProcesses()
        .get(0)
        .getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(initialVariables)
            .create();

    // then
    final long flowScopeKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sub")
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(flowScopeKey)
                .limit(expectedActivityVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .hasSize(expectedActivityVariables.size())
        .containsAll(expectedActivityVariables);
  }

  private static Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mapping(
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<VariableValue> activityVariables(final VariableValue... variables) {
    return Arrays.asList(variables);
  }
}
