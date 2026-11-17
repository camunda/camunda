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
 * Characterization tests for {@link EngineConfiguration.OutputMappingMode#ORDERED}: output mappings
 * are evaluated in declaration order, with each later mapping seeing the accumulated results of all
 * earlier ones. COMBINED is the default; ORDERED is the opt-in mode for users who need the newer
 * per-mapping semantics.
 *
 * <p>These tests are the mirror of {@link CombinedOutputMappingModeTest} and document where ORDERED
 * and COMBINED semantics diverge on duplicate-target scenarios.
 */
public final class OrderedOutputMappingModeTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setOutputMappingMode(EngineConfiguration.OutputMappingMode.ORDERED));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldEvaluateMappingsInOrderAndReadIntermediateValues() {
    // given: the canonical duplicate-target example (1 -> x, x -> y, 2 -> x).
    // With ORDERED mode every mapping is evaluated sequentially against the current scope:
    // mapping-1 sets x=1; mapping-2 reads x (=1 at this point) and sets y=1; mapping-3 sets x=2.
    // Final state: x=2 (last write wins), y=1 (captured x before the overwrite).
    // With COMBINED mode x is resolved to its last-winning value in the pre-built FEEL context
    // expression, so y reads x=2 instead — giving y=2.
    final var process =
        Bpmn.createExecutableProcess("process-ordered-duplicate-target")
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
        ENGINE.processInstance().ofBpmnProcessId("process-ordered-duplicate-target").create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("test").complete();

    // then: x=2 (last write wins) and y=1 (mapping-2 read x before mapping-3 overwrote it)
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .containsExactlyInAnyOrder(variable("x", "2"), variable("y", "1"));
  }

  @Test
  public void shouldRaiseIncidentWhenSupersededDuplicateSourceFails() {
    // given: two output mappings targeting the same key; the first one's source always fails.
    // With ORDERED mode every source is evaluated in declaration order, so the failing source is
    // reached and an incident is raised. With COMBINED mode the first mapping's source is dropped
    // when building the combined FEEL context expression, silently suppressing the incident.
    final var process =
        Bpmn.createExecutableProcess("process-ordered-duplicate-target-fail")
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
        ENGINE.processInstance().ofBpmnProcessId("process-ordered-duplicate-target-fail").create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("test").complete();

    // then: an incident is raised because the failing source is evaluated
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }
}
