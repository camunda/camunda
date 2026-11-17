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
 * Regression tests for output-mapping scenarios that require COMBINED mode's context-literal
 * sibling scoping. The resolver is pinned to COMBINED explicitly so these tests remain stable
 * across future default changes.
 *
 * <p>The key behavioral difference from ORDERED: all output mapping sources are evaluated against
 * the job-completion scope simultaneously via a single pre-built FEEL context literal. Later
 * mappings do NOT see results written by earlier mappings, but siblings within the same nested
 * context-literal expression ARE visible to each other (standard FEEL context literal semantics).
 *
 * @see ActivityOutputMappingOrderedTest
 * @see CombinedOutputMappingModeTest
 */
public final class ActivityOutputMappingCombinedTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setOutputMappingMode(EngineConfiguration.OutputMappingMode.COMBINED));

  private static final String JOB_TYPE = "test";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final TestName testName = new TestName();

  @Test
  public void shouldPreserveExistingSiblingKeysAtNestedOutputTarget() {
    // Verifies the context merge() behavior in the pre-built combined FEEL expression for nested
    // targets. With COMBINED mode, the transformer generates:
    //   {a: if (a != null) then context merge(a, {b:x}) else {b:x}}
    // so the existing sibling key 'c' in the process scope is preserved — restoring the pre-#59087
    // behavior. With ORDERED, OutputMappingResultBuilder seeds from scope to the same effect.

    // given — process scope has a = {b: "old", c: "kept"}
    final String processId = "process-" + testName.getMethodName();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task", t -> t.zeebeJobType(JOB_TYPE).zeebeOutputExpression("x", "a.b"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{\"a\":{\"b\":\"old\",\"c\":\"kept\"}}")
            .create();

    // when — job completes with x=42
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withVariables("{\"x\":42}")
        .complete();

    // then — a.b updated to 42, a.c preserved via context merge
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .withName("a")
                .limit(2) // initial set + update after job completion
                .getLast()
                .getValue()
                .getValue())
        .isEqualTo("{\"b\":42,\"c\":\"kept\"}");
  }

  @Test
  public void shouldResolveBareSiblingReferenceInNestedContextWithCombinedDefault() {
    // Regression for the GitHub Outbound Connector pattern: the authentication.token
    // mapping depends on bare-name sibling resolution within a nested FEEL context literal.
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
                    "sub",
                    b -> {
                      b.embeddedSubProcess()
                          .startEvent()
                          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                          .endEvent();
                      b.zeebeOutputExpression("=\"github\"", "authentication.type")
                          .zeebeOutputExpression("=type", "authentication.final");
                    })
                .endEvent()
                .done())
        .deploy();

    // when — no job variables needed; the first mapping is a literal
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

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
                .limit(1))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactly(variable("authentication", "{\"type\":\"github\",\"final\":\"github\"}"));
  }
}
