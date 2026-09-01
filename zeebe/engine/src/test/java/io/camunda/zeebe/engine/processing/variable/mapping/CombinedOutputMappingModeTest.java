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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Characterization tests for {@link EngineConfiguration.OutputMappingMode#COMBINED}: output
 * mappings are evaluated by a single pre-built FEEL context expression that mirrors the pre-{@code
 * #59087} combined-FEEL-context-builder behavior. Duplicate targets are resolved at deploy time
 * when building the context expression — later mappings to the same key overwrite earlier ones, so
 * the failing source of a superseded duplicate is never evaluated. This mode acts as a kill-switch
 * for users who depended on the old single-expression evaluation semantics.
 */
public final class CombinedOutputMappingModeTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setOutputMappingMode(EngineConfiguration.OutputMappingMode.COMBINED));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldReproduceOldValuesForInterleavedDuplicateTargets() {
    // given: the PR's canonical duplicate-target example (1 -> x, x -> y, 2 -> x); with
    // ORDERED mode this yields x=2, y=1 (see ActivityOutputMappingTest#shouldApplyOutputMappings),
    // but the COMBINED mode builds a single FEEL context expression {x:2, y:x} where duplicate
    // target 'x' is resolved at deploy time to its last-winning value (2); within the FEEL context
    // literal, 'y:x' sees the earlier sibling entry 'x:2', so y=2
    final var process =
        Bpmn.createExecutableProcess("process-duplicate-target-old-semantics")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("test")
                        .zeebeOutputExpression("1", "x")
                        .zeebeOutputExpression("x", "y")
                        .zeebeOutputExpression("2", "x"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process-duplicate-target-old-semantics").create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("test").complete();

    // then: x=2 (last write wins) and y=2 (not y=1, the ORDERED mode value); the output mapping
    // is applied while the task is completing, so its variable merge is exported before the
    // task's own ELEMENT_COMPLETED record
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(variable("x", "2"), variable("y", "2"));
  }

  @Test
  public void shouldSuppressIncidentFromSupersededDuplicateOutputMappingSource() {
    // given: two output mappings targeting the same key; the first one's source always fails.
    // With ORDERED mode (default), every source is evaluated in modeling order, so this fails and
    // raises an incident (see
    // MappingIncidentTest#shouldRaiseIncidentForPreviouslyDroppedDuplicateOutputMappingSource).
    // With COMBINED mode, the combined FEEL context expression is built at deploy time: the
    // duplicate target 'x' is resolved to its last-winning source ('2'), so the failing first
    // source never appears in the expression and the incident is suppressed.
    final var process =
        Bpmn.createExecutableProcess("process-duplicate-target-fail-suppressed")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("test")
                        .zeebeOutputExpression("assert(missing, missing != null)", "x")
                        .zeebeOutputExpression("2", "x"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process-duplicate-target-fail-suppressed")
            .create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("test").complete();

    // then: no incident is raised, and the surviving (later) mapping's value wins
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .limit(1))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactly(variable("x", "2"));

    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                ignored ->
                    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                        .withProcessInstanceKey(processInstanceKey)
                        .exists()))
        .isFalse();
  }
}
