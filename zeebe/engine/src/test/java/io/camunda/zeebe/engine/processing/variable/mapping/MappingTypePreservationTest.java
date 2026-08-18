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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * A mapping's result reaches the next mapping in the same list as the FEEL value it is, not as
 * MessagePack. MessagePack has no representation for a duration, date or time, so a value that had
 * to round trip through it came back as a plain string — a duration written by one mapping reached
 * the next as text, and reading a field of it, such as {@code x.days}, was {@code null}.
 *
 * <p>The stored variable is unaffected: it is still serialized once, at the end of the mapping
 * list, so it holds exactly what the storage format holds. Only what a <em>later mapping</em>
 * observes changes.
 *
 * <p>Regression tests for <a href="https://github.com/camunda/camunda/issues/60011">#60011</a>,
 * input-mapping rule 8.
 */
public final class MappingTypePreservationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String MAPPED_ELEMENT_ID = "mapped";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final TestName testName = new TestName();

  @Test
  public void shouldKeepDurationTypeAcrossInputMappings() {
    // given: x is a duration; y reads a field that only the FEEL value has, not its MessagePack
    // (string) encoding
    final long processInstanceKey =
        activate(
            "{}",
            b ->
                b.zeebeInputExpression("duration(\"P1DT2H\")", "x")
                    .zeebeInputExpression("x.days", "y"));

    // then: x is still stored as the plain string the storage format holds; y proves the second
    // mapping read x as the duration itself
    assertMappedElementVariables(
        processInstanceKey, variable("x", "\"P1DT2H\""), variable("y", "1"));
  }

  /**
   * Deploys a process whose single sub-process carries the given input mappings, starts an instance
   * with the given variables, and returns its key.
   */
  private long activate(
      final String initialVariables,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings) {
    final String processId = processId();
    ENGINE.deployment().withXmlResource(processWithMappings(processId, mappings)).deploy();
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(initialVariables)
        .create();
  }

  private static BpmnModelInstance processWithMappings(
      final String processId,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .subProcess(
            MAPPED_ELEMENT_ID,
            b -> {
              b.embeddedSubProcess().startEvent().endEvent();
              mappings.accept(b);
            })
        .endEvent()
        .done();
  }

  /** Asserts the local variables of the mapped element instance, and only those. */
  private void assertMappedElementVariables(
      final long processInstanceKey, final VariableValue... expected) {
    final long mappedElementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(MAPPED_ELEMENT_ID)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(mappedElementInstanceKey)
                .limit(expected.length))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(expected);
  }

  /** A process id per test method, so deployments and instances never collide. */
  private String processId() {
    return "process-" + testName.getMethodName();
  }
}
