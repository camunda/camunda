/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import io.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MessageOutputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_VARIABLE = "key";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String messageVariables;

  @Parameter(1)
  public Consumer<IntermediateCatchEventBuilder> mappings;

  @Parameter(2)
  public List<Tuple> expectedActivityVariables;

  @Parameter(3)
  public List<Tuple> expectedScopeVariables;

  private String correlationKey;

  @Parameters(name = "from {0} to activity: {2} and scope: {3}")
  public static Object[][] parameters() {
    return new Object[][] {
      // create variable
      {"{'x': 1}", mapping(b -> {}), activityVariables(), scopeVariables(tuple("x", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("x", "x")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("x", "1"))
      },
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("x", "y")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("y", "1"))
      },
      {
        "{'x': 1, 'y': 2}",
        mapping(b -> b.zeebeOutput("y", "z")),
        activityVariables(tuple("x", "1"), tuple("y", "2")),
        scopeVariables(tuple("z", "2"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> {}),
        activityVariables(),
        scopeVariables(tuple("x", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutput("x", "y")),
        activityVariables(tuple("x", "{\"y\":2}")),
        scopeVariables(tuple("y", "{\"y\":2}"))
      },
      {
        "{'x': {'y': 2}}",
        mapping(b -> b.zeebeOutput("x.y", "y")),
        activityVariables(tuple("x", "{\"y\":2}")),
        scopeVariables(tuple("y", "2"))
      },
      // update variable
      {"{'i': 1}", mapping(b -> {}), activityVariables(), scopeVariables(tuple("i", "1"))},
      {
        "{'x': 1}",
        mapping(b -> b.zeebeOutput("x", "i")),
        activityVariables(tuple("x", "1")),
        scopeVariables(tuple("i", "1"))
      },
      {
        "{'i': 2, 'x': 1}",
        mapping(b -> b.zeebeInput("i", "x")),
        activityVariables(tuple("x", "0")),
        scopeVariables(tuple("i", "2"))
      },
    };
  }

  @Before
  public void init() {
    correlationKey = UUID.randomUUID().toString();
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent(
                    "catch-event",
                    b -> {
                      b.message(
                          m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE));

                      mappings.accept(b);
                    })
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getDeployedWorkflows()
        .get(0)
        .getWorkflowKey();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("i", 0);
    variables.put(CORRELATION_VARIABLE, correlationKey);

    // when
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{'i':0,'key':'" + correlationKey + "'}")
            .create();
    ENGINE_RULE
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(correlationKey)
        .withVariables(MsgPackUtil.asMsgPack(messageVariables))
        .publish();

    // then
    final long elementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("catch-event")
            .getFirst()
            .getKey();

    final Record<VariableRecordValue> initialVariable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName(CORRELATION_VARIABLE)
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .skipUntil(r -> r.getPosition() > initialVariable.getPosition())
                .withScopeKey(elementInstanceKey)
                .limit(expectedActivityVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedActivityVariables.size())
        .containsAll(expectedActivityVariables);

    assertThat(
            RecordingExporter.variableRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .skipUntil(r -> r.getPosition() > initialVariable.getPosition())
                .withScopeKey(workflowInstanceKey)
                .limit(expectedScopeVariables.size()))
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getName(), v.getValue()))
        .hasSize(expectedScopeVariables.size())
        .containsAll(expectedScopeVariables);
  }

  private static Consumer<ZeebeVariablesMappingBuilder<IntermediateCatchEventBuilder>> mapping(
      Consumer<ZeebeVariablesMappingBuilder<IntermediateCatchEventBuilder>> mappingBuilder) {
    return mappingBuilder;
  }

  private static List<Tuple> activityVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }

  private static List<Tuple> scopeVariables(Tuple... variables) {
    return Arrays.asList(variables);
  }
}
