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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Characterization tests for the {@code evaluateInputMappingsOneByOne} kill-switch: with the flag
 * disabled, all input mappings of an element are evaluated as the single combined FEEL context
 * expression they compiled into before #58801, reinstating the behavior of versions up to 8.7.35
 * and 8.8.33 — including the bugs that change fixed.
 *
 * <p>Each case here is the mirror image of one in {@link ActivityInputMappingTest}, which asserts
 * the same process under the default (enabled) flag.
 */
public final class InputMappingOneByOneFeatureFlagTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withFeatureFlags(f -> f.setEvaluateInputMappingsOneByOne(false));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldKeepSiblingFieldsOfTheSameRootWhenDisabled() {
    // given: the #59646 scenario. Unlike the two cases below, this is not a bug the combined
    // expression had - it is one that evaluating mappings one by one INTRODUCED, which is why the
    // issue reports it against 8.7.36/8.8.34, the first patches carrying #58801.
    // In the combined expression {foo: {bar: foo.bar, baz: foo.baz}} the sources sit in a NESTED
    // context, and feel-scala does not expose the enclosing context's still-unevaluated 'foo'
    // entry to them, so both resolve against the parent scope.
    final var variables =
        applyInputMappings(
            "{'foo': {'bar': 1, 'baz': 2}}",
            b ->
                b.zeebeInputExpression("foo.bar", "foo.bar")
                    .zeebeInputExpression("foo.baz", "foo.baz"),
            1);

    // then: the same result the one-by-one path produces once #59646 is fixed - the kill-switch
    // is an escape hatch from that regression too, at the price of the two bugs below
    assertThat(variables).containsExactly(variable("foo", "{\"bar\":1,\"baz\":2}"));
  }

  @Test
  public void shouldNotSeeAnInterleavedMappingAcrossRegroupedTargetsWhenDisabled() {
    // given: the #11789 scenario. Regrouping hoists both 'obj.*' entries ahead of 'flat', so
    // 'obj.second' resolves 'flat' from the outer scope, where it is absent
    final var variables =
        applyInputMappings(
            "{}",
            b ->
                b.zeebeInputExpression("1", "obj.first")
                    .zeebeInputExpression("2", "flat")
                    .zeebeInputExpression("flat", "obj.second"),
            2);

    // then
    assertThat(variables)
        .contains(variable("obj", "{\"first\":1,\"second\":null}"), variable("flat", "2"));
  }

  @Test
  public void shouldEvaluateOnlyTheLastDuplicateTargetSourceWhenDisabled() {
    // given: duplicate leaf targets collapse into one context entry that keeps its FIRST
    // occurrence's position but the LAST one's source, so 'y' reads the already-overwritten 'x'
    final var variables =
        applyInputMappings(
            "{}",
            b ->
                b.zeebeInputExpression("1", "x")
                    .zeebeInputExpression("x", "y")
                    .zeebeInputExpression("2", "x"),
            2);

    // then: with the flag enabled this is x=2, y=1
    assertThat(variables).contains(variable("x", "2"), variable("y", "2"));
  }

  @Test
  public void shouldStillApplyUnaffectedMappingsWhenDisabled() {
    // given: mappings that do not depend on evaluation order behave identically either way, so
    // the kill-switch is not a blanket regression
    final var variables =
        applyInputMappings(
            "{'x': 1, 'y': 2}",
            b -> b.zeebeInputExpression("x", "a").zeebeInputExpression("y", "b.c"),
            2);

    // then
    assertThat(variables).contains(variable("a", "1"), variable("b", "{\"c\":2}"));
  }

  private List<VariableValue> applyInputMappings(
      final String initialVariables,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings,
      final int expectedVariableCount) {
    final var processId = "process-" + UUID.randomUUID();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sub",
                b -> {
                  b.embeddedSubProcess().startEvent().endEvent();
                  mappings.accept(b);
                })
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(initialVariables)
            .create();

    final long subProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sub")
            .getFirst()
            .getKey();

    return RecordingExporter.variableRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withScopeKey(subProcessKey)
        .limit(expectedVariableCount)
        .map(Record::getValue)
        .map(v -> variable(v.getName(), v.getValue()))
        .toList();
  }
}
