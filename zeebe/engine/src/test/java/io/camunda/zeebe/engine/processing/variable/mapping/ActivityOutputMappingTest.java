/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static io.camunda.zeebe.engine.processing.variable.mapping.VariableValue.variable;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
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

  private static final String A_TIME_VALUE = "\"04:20:00@Europe/Berlin\"";
  private static final String A_LOCAL_TIME_VALUE = "\"04:20\"";
  private static final String A_DATE_VALUE = "\"2021-02-24\"";
  private static final String A_LOCAL_DATE_AND_TIME_VALUE = "\"2021-02-24T04:20:00\"";
  private static final String A_DATE_AND_TIME_VALUE = "\"2021-02-24T04:20:00+01:00\"";
  private static final String A_DAY_TIME_DURATION_VALUE = "\"P1DT18H56M33S\"";
  private static final String A_YEAR_MONTH_DURATION_VALUE = "\"P2Y3M\"";

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
        "{'_x': 1}",
        mapping(b -> b.zeebeOutputExpression("_x", "_y")),
        scopeVariables(variable("_y", "1"))
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
      {
        "{'x': %s}".formatted(A_TIME_VALUE),
        mapping(b -> b.zeebeOutputExpression("time(x)", "y")),
        scopeVariables(variable("y", A_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_LOCAL_TIME_VALUE),
        mapping(b -> b.zeebeOutputExpression("time(x)", "y")),
        scopeVariables(variable("y", A_LOCAL_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_DATE_VALUE),
        mapping(b -> b.zeebeOutputExpression("date(x)", "y")),
        scopeVariables(variable("y", A_DATE_VALUE))
      },
      {
        "{'x': %s}".formatted(A_LOCAL_DATE_AND_TIME_VALUE),
        mapping(b -> b.zeebeOutputExpression("date and time(x)", "y")),
        scopeVariables(variable("y", A_LOCAL_DATE_AND_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_DATE_AND_TIME_VALUE),
        mapping(b -> b.zeebeOutputExpression("date and time(x)", "y")),
        scopeVariables(variable("y", A_DATE_AND_TIME_VALUE))
      },
      {
        "{'x': %s}".formatted(A_DAY_TIME_DURATION_VALUE),
        mapping(b -> b.zeebeOutputExpression("duration(x)", "y")),
        scopeVariables(variable("y", A_DAY_TIME_DURATION_VALUE))
      },
      {
        "{'x': %s}".formatted(A_YEAR_MONTH_DURATION_VALUE),
        mapping(b -> b.zeebeOutputExpression("duration(x)", "y")),
        scopeVariables(variable("y", A_YEAR_MONTH_DURATION_VALUE))
      }
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
