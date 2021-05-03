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
import io.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ActivityOutputMappingTest {

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
  public List<VariableValue> expectedScopeVariables;

  @Parameters(name = "from {0} to {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutputExpression("x", "y")),
        scopeVariables(variable("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutputExpression("y", "z")),
        scopeVariables(variable("z", "2"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("x", "y").zeebeOutputExpression("y", "z")),
        scopeVariables(variable("z", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("x", "y").zeebeOutputExpression("x", "z")),
        scopeVariables(variable("z", "1"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutputExpression("x", "z")),
        scopeVariables(variable("z", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutputExpression("x.y", "z")),
        scopeVariables(variable("z", "2"))
      },
      {
        "{'z': {'x': 1}, 'y': 2}",
        mapping(b -> b.zeebeOutputExpression("y", "z.y")),
        scopeVariables(variable("z", "{\"x\":1,\"y\":2}"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutputExpression("x", "z.x").zeebeOutputExpression("y", "z.y")),
        scopeVariables(variable("z", "{\"x\":1,\"y\":2}"))
      },
    };
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    final String jobType = UUID.randomUUID().toString();

    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "sub",
                    b -> {
                      b.embeddedSubProcess()
                          .startEvent()
                          .serviceTask("task", t -> t.zeebeJobType(jobType))
                          .endEvent();

                      mappings.accept(b);
                    })
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();

    // when
    final long processInstanceKey =
        ENGINE_RULE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(initialVariables)
            .create();

    ENGINE_RULE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then
    final Record<ProcessInstanceRecordValue> taskCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > taskCompleted.getPosition())
                .withScopeKey(processInstanceKey)
                .limit(expectedScopeVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .hasSize(expectedScopeVariables.size())
        .containsAll(expectedScopeVariables);
  }

  private static Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mapping(
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<VariableValue> scopeVariables(final VariableValue... variables) {
    return Arrays.asList(variables);
  }
}
