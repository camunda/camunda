/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static io.camunda.zeebe.engine.processing.variable.mapping.VariableValue.variable;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class JobOutputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String jobVariables;

  @Parameter(1)
  public Consumer<ServiceTaskBuilder> mappings;

  @Parameter(2)
  public List<VariableValue> expectedActivityVariables;

  @Parameter(3)
  public List<VariableValue> expectedScopeVariables;

  private String jobType;

  @Parameters(name = "from {0} to activity: {2} and scope: {3}")
  public static Object[][] parameters() {
    return new Object[][] {
      // create variable
      {"{'x': 1}", mapping(b -> {}), activityVariables(), scopeVariables(variable("x", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutputExpression("x", "x")),
        activityVariables(variable("x", "1")),
        scopeVariables(variable("x", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutputExpression("x", "y")),
        activityVariables(variable("x", "1")),
        scopeVariables(variable("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutputExpression("y", "z")),
        activityVariables(variable("x", "1"), variable("y", "2")),
        scopeVariables(variable("z", "2"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> {}),
        activityVariables(),
        scopeVariables(variable("x", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutputExpression("x", "y")),
        activityVariables(variable("x", "{\"y\":2}")),
        scopeVariables(variable("y", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutputExpression("x.y", "y")),
        activityVariables(variable("x", "{\"y\":2}")),
        scopeVariables(variable("y", "2"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutputExpression("x", "z.x").zeebeOutputExpression("y", "z.y")),
        activityVariables(variable("x", "1"), variable("y", "2")),
        scopeVariables(variable("z", "{\"x\":1,\"y\":2}"))
      },
      // update variable
      {"{'i': 1}", mapping(b -> {}), activityVariables(), scopeVariables(variable("i", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutputExpression("x", "i")),
        activityVariables(variable("x", "1")),
        scopeVariables(variable("i", "1"))
      },
      // combine input and output mapping
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("i", "y").zeebeOutputExpression("y", "z")),
        activityVariables(variable("x", "1"), variable("y", "0")),
        scopeVariables(variable("z", "0"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("i", "x").zeebeOutputExpression("x", "y")),
        activityVariables(variable("x", "0"), variable("x", "1")),
        scopeVariables(variable("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(
            b ->
                b.zeebeInputExpression("i", "x")
                    .zeebeInputExpression("i", "y")
                    .zeebeOutputExpression("y", "z")),
        activityVariables(
            variable("x", "0"), variable("y", "0"), variable("x", "1"), variable("y", "2")),
        scopeVariables(variable("z", "2"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeInputExpression("i", "y")),
        activityVariables(variable("y", "0")),
        scopeVariables(variable("x", "1"))
      },
      {
        "{'z': 1, 'j': 1}",
        mapping(b -> b.zeebeInputExpression("i", "z")),
        activityVariables(variable("z", "0")),
        scopeVariables(variable("j", "1"))
      },
    };
  }

  @Before
  public void init() {
    jobType = UUID.randomUUID().toString();
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "task",
                    builder -> {
                      builder.zeebeJobType(jobType);
                      mappings.accept(builder);
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
        ENGINE_RULE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("i", 0).create();

    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType(jobType)
        .withVariables(MsgPackUtil.asMsgPack(jobVariables))
        .complete();

    // then
    final long elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst()
            .getKey();

    final Record<VariableRecordValue> initialVariable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("i")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > initialVariable.getPosition())
                .withScopeKey(elementInstanceKey)
                .limit(expectedActivityVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .hasSize(expectedActivityVariables.size())
        .containsAll(expectedActivityVariables);

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > initialVariable.getPosition())
                .withScopeKey(processInstanceKey)
                .limit(expectedScopeVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .hasSize(expectedScopeVariables.size())
        .containsAll(expectedScopeVariables);
  }

  private static Consumer<ZeebeVariablesMappingBuilder<ServiceTaskBuilder>> mapping(
      final Consumer<ZeebeVariablesMappingBuilder<ServiceTaskBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<VariableValue> activityVariables(final VariableValue... variables) {
    return Arrays.asList(variables);
  }

  private static List<VariableValue> scopeVariables(final VariableValue... variables) {
    return Arrays.asList(variables);
  }
}
