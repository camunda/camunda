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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Covers output-mapping scenarios that require ORDERED semantics — cross-mapping references that
 * depend on sequential evaluation. Pinned to ORDERED explicitly so these tests remain stable across
 * future default changes.
 *
 * <p>The scenarios here were removed from the default-mode {@link ActivityOutputMappingTest}
 * because they only pass under ORDERED; under COMBINED they produce different results.
 *
 * @see ActivityOutputMappingCombinedTest
 */
public final class ActivityOutputMappingOrderedTest {

  @ClassRule
  public static final EngineRule ENGINE_RULE =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setOutputMappingMode(EngineConfiguration.OutputMappingMode.ORDERED));

  private static final String JOB_TYPE = "test";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final TestName testName = new TestName();

  @Test
  // regression test for https://github.com/camunda/camunda/issues/11789
  public void shouldReadEarlierOutputMappingResultInLaterMapping() {
    // Mapping 1 writes 1 to obj.first. Mapping 2 writes 2 to flat. Mapping 3 reads flat (=2) into
    // obj.second. Mapping 4 copies flat into flatCopy. With ORDERED, each later mapping sees the
    // accumulated results of all earlier ones.

    // given
    final String processId = "process-" + testName.getMethodName();
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    b -> {
                      b.embeddedSubProcess()
                          .startEvent()
                          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                          .endEvent();
                      b.zeebeOutputExpression("1", "obj.first")
                          .zeebeOutputExpression("2", "flat")
                          .zeebeOutputExpression("flat", "obj.second")
                          .zeebeOutputExpression("flat", "flatCopy");
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE_RULE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    final Record<io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue> taskCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > taskCompleted.getPosition())
                .withScopeKey(processInstanceKey)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(
            variable("flat", "2"),
            variable("obj", "{\"first\":1,\"second\":2}"),
            variable("flatCopy", "2"));
  }

  @Test
  public void shouldResolveForwardReferenceToNullUnderOrdered() {
    // Mapping 1 reads 'late' (not yet defined) into 'early' — resolves to null at this position.
    // Mapping 2 writes 1 to 'late'. With ORDERED, the forward reference resolves to null because
    // 'late' has not been written yet when mapping 1 is evaluated.

    // given
    final String processId = "process-" + testName.getMethodName();
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    b -> {
                      b.embeddedSubProcess()
                          .startEvent()
                          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                          .endEvent();
                      b.zeebeOutputExpression("late", "early").zeebeOutputExpression("1", "late");
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE_RULE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    final Record<io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue> taskCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > taskCompleted.getPosition())
                .withScopeKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(variable("early", "null"), variable("late", "1"));
  }

  @Test
  public void shouldReadNestedTargetValueInLaterOutputMapping() {
    // Mapping 1 writes job variable 'x' (=1) to nested target 'a.b'. Mapping 2 reads 'a.b + 1'
    // (=2) into 'c'. With ORDERED, mapping 2 sees the merged nested result of mapping 1.

    // given
    final String processId = "process-" + testName.getMethodName();
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    b -> {
                      b.embeddedSubProcess()
                          .startEvent()
                          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                          .endEvent();
                      b.zeebeOutputExpression("x", "a.b").zeebeOutputExpression("a.b + 1", "c");
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE_RULE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withVariables(Map.of("x", 1))
        .complete();

    // then
    final Record<io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue> taskCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > taskCompleted.getPosition())
                .withScopeKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(variable("a", "{\"b\":1}"), variable("c", "2"));
  }

  @Test
  public void shouldReadIntermediateValueWhenDuplicateTargetOverrides() {
    // Mapping 1 writes 1 to x. Mapping 2 reads x (=1) into y — capturing the snapshot value at
    // this position. Mapping 3 overwrites x with 2. Final state with ORDERED: x=2, y=1.
    // Under COMBINED the result is different: y=2 (see ActivityOutputMappingCombinedTest).

    // given
    final String processId = "process-" + testName.getMethodName();
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "sub",
                    b -> {
                      b.embeddedSubProcess()
                          .startEvent()
                          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                          .endEvent();
                      b.zeebeOutputExpression("1", "x")
                          .zeebeOutputExpression("x", "y")
                          .zeebeOutputExpression("2", "x");
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE_RULE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE_RULE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    final Record<io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue> taskCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > taskCompleted.getPosition())
                .withScopeKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(variable("x", "2"), variable("y", "1"));
  }
}
