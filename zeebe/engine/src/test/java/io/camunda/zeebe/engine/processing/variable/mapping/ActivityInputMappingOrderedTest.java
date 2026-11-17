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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Covers input-mapping scenarios that require ORDERED semantics — cross-mapping references that
 * depend on the sequential evaluation model. Pinned to ORDERED explicitly so these tests remain
 * stable across future default changes.
 */
public final class ActivityInputMappingOrderedTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setInputMappingMode(EngineConfiguration.InputMappingMode.ORDERED));

  private static final String MAPPED_ELEMENT_ID = "mapped";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final TestName testName = new TestName();

  @Test
  // regression test for https://github.com/camunda/camunda/issues/11789
  public void shouldSeeEarlierMappingResultInLaterSource() {
    // Mapping 1 writes 1 to obj.first. Mapping 2 writes 2 to flat. Mapping 3 reads flat (=2)
    // into obj.second. Mapping 4 copies flat into flatCopy. With ORDERED, each later mapping sees
    // the accumulated results of all earlier ones.

    // given
    final String processId = "process-" + testName.getMethodName();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    MAPPED_ELEMENT_ID,
                    b -> {
                      b.embeddedSubProcess().startEvent().endEvent();
                      b.zeebeInputExpression("1", "obj.first")
                          .zeebeInputExpression("2", "flat")
                          .zeebeInputExpression("flat", "obj.second")
                          .zeebeInputExpression("flat", "flatCopy");
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final long scopeKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(MAPPED_ELEMENT_ID)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(scopeKey)
                .limit(3))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(
            variable("flat", "2"),
            variable("obj", "{\"first\":1,\"second\":2}"),
            variable("flatCopy", "2"));
  }

  @Test
  public void shouldReadSnapshotValueWhenDuplicateTargetOverridesEarlierResult() {
    // Mapping 1 writes 1 to x. Mapping 2 reads x (=1) into y — capturing the value at this
    // position. Mapping 3 overwrites x with 2. Final state: x=2, y=1 (not y=2).

    // given
    final String processId = "process-" + testName.getMethodName();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    MAPPED_ELEMENT_ID,
                    b -> {
                      b.embeddedSubProcess().startEvent().endEvent();
                      b.zeebeInputExpression("1", "x")
                          .zeebeInputExpression("x", "y")
                          .zeebeInputExpression("2", "x");
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final long scopeKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(MAPPED_ELEMENT_ID)
            .getFirst()
            .getKey();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(scopeKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(variable("x", "2"), variable("y", "1"));
  }
}
