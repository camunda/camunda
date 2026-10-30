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
 * Regression tests for input-mapping scenarios that require COMBINED mode's context-literal sibling
 * scoping. The resolver is pinned to COMBINED explicitly so these tests remain stable across future
 * default changes.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/60551">#60551</a>
 */
public final class ActivityInputMappingCombinedTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setInputMappingMode(EngineConfiguration.InputMappingMode.COMBINED));

  private static final String MAPPED_ELEMENT_ID = "mapped";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final TestName testName = new TestName();

  @Test
  public void shouldResolveBareSiblingReferenceInNestedContextWithCombinedDefault() {
    // Regression for #60551: the GitHub Outbound Connector's authentication.token
    // mapping depends on this bare-name sibling resolution. This test fails with ORDERED default.
    //
    // Mapping 1 writes a literal string to authentication.type. Mapping 2 reads `type` as a bare
    // name: with COMBINED, the FEEL context built so far is available as a context-literal, so
    // `type` resolves to the sibling value "github" written by mapping 1. With ORDERED, `type` is
    // not in scope and resolves to null.

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
                      b.zeebeInputExpression("=\"github\"", "authentication.type")
                          .zeebeInputExpression("=type", "authentication.final");
                    })
                .endEvent()
                .done())
        .deploy();

    // when — no scope variables needed; the first mapping is a literal
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
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
                .limit(1))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactly(variable("authentication", "{\"type\":\"github\",\"final\":\"github\"}"));
  }
}
