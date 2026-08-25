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

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
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

/**
 * Covers the two input-mapping scenarios from {@link ActivityInputMappingTest} that require ORDERED
 * semantics — cross-mapping forward references that depend on the sequential evaluation model.
 * These are pinned to ORDERED mode explicitly so they continue to pass after the default mode is
 * changed to COMBINED.
 */
@RunWith(Parameterized.class)
public final class ActivityInputMappingOrderedTest {

  @ClassRule
  public static final EngineRule ENGINE_RULE =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setInputMappingMode(EngineConfiguration.InputMappingMode.ORDERED));

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
        // mappings are evaluated in modeling order, so a later mapping can reference the target of
        // an earlier one even when nested targets are interleaved with plain ones
        // regression test for https://github.com/camunda/camunda/issues/11789
        "{}",
        mapping(
            b ->
                b.zeebeInputExpression("1", "obj.first")
                    .zeebeInputExpression("2", "flat")
                    .zeebeInputExpression("flat", "obj.second")
                    .zeebeInputExpression("flat", "flatCopy")),
        activityVariables(
            variable("flat", "2"),
            variable("obj", "{\"first\":1,\"second\":2}"),
            variable("flatCopy", "2"))
      },
      {
        // duplicate targets: every mapping is evaluated in order, the last value wins;
        // an intermediate mapping sees the value that was current at its position
        "{}",
        mapping(
            b ->
                b.zeebeInputExpression("1", "x")
                    .zeebeInputExpression("x", "y")
                    .zeebeInputExpression("2", "x")),
        activityVariables(variable("x", "2"), variable("y", "1"))
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
