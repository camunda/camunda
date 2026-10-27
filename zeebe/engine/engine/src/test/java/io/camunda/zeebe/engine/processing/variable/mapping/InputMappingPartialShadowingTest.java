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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Input mappings shadow a same-named variable from a higher scope only <em>partially</em>: a later
 * source that reads the name sees the keys the earlier mappings defined layered over the value the
 * scope chain resolves to, instead of replacing it outright.
 *
 * <p>The layering is a read-time concern only. The local variable that is written stays exactly
 * what the mappings targeted — a mapping to {@code x.a} still creates a local {@code x} holding
 * nothing but {@code a}. Every scenario below therefore asserts <em>both</em> the mapped root and
 * the variable that read it; asserting only the reader would also pass for an implementation that
 * seeds the accumulated document from the scope value, which is not the agreed behaviour.
 *
 * <p>Fall-through adds exactly one layer, however deep the scope chain goes: rule 2's walk up the
 * chain stops at the first scope holding the name and takes that whole value, so only the value the
 * chain resolves to is layered under the mapping — not every same-named variable on the way to the
 * root.
 *
 * <p>Partial shadowing is only meaningful where both sides are objects. A mapping that assigns a
 * whole name — a scalar, a null, or an object — shadows the ancestor totally, because there is
 * nothing left to fall through to.
 *
 * <p>Regression tests for <a href="https://github.com/camunda/camunda/issues/59646">#59646</a>.
 */
public final class InputMappingPartialShadowingTest {

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

  // ---------------------------------------------------------------------------------------------
  // Total shadowing: a mapping that assigns a whole name leaves nothing to fall through to
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldTotallyShadowAncestorWhenTheWholeNameIsMappedToNull() {
    // given: x is mapped as a whole, so there is no partial anything to fall through to. This is
    // the guard against an implementation that layers scalars and nulls over the ancestor too:
    // there is no "absent" state, an assigned target is authoritative.
    assertInputMapping(
        "{'x': 5}",
        b -> b.zeebeInputExpression("null", "x").zeebeInputExpression("x", "y"),
        variable("x", "null"),
        variable("y", "null"));
  }

  @Test
  public void shouldTotallyShadowAncestorWhenTheWholeNameIsMappedToAnObject() {
    // given: the mapped value is an object, but it was assigned to x as a whole rather than to a
    // path inside it, so the ancestor's b is not reachable under x anymore
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b -> b.zeebeInputExpression("{a: 3}", "x").zeebeInputExpression("x", "y"),
        variable("x", "{'a':3}"),
        variable("y", "{'a':3}"));
  }

  // ---------------------------------------------------------------------------------------------
  // Scenario 7.2 — narrowing an object onto its own name (issue #59646)
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepSiblingFieldsWhenNarrowingAnObjectOntoItsOwnName() {
    // given: the "keep only the fields this task needs, under the same name" idiom. The second
    // mapping's source must still resolve x against the ancestor for the key the first mapping did
    // not define.
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b -> b.zeebeInputExpression("x.a", "x.a").zeebeInputExpression("x.b", "x.b"),
        variable("x", "{'a':1,'b':2}"));
  }

  @Test
  public void shouldKeepSiblingFieldsWhenNarrowingAnObjectOntoItsOwnNameInReverseOrder() {
    // given: the same shape with the two mappings swapped — partial shadowing makes the result
    // order-independent, where total shadowing lets declaration order pick which field survives
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b -> b.zeebeInputExpression("x.b", "x.b").zeebeInputExpression("x.a", "x.a"),
        variable("x", "{'a':1,'b':2}"));
  }

  @Test
  public void shouldKeepSiblingFieldsWhenNarrowingANestedObjectOntoItsOwnName() {
    // given: the same shape one level deeper. This is the case that separates a recursive merge
    // from a top-level-only one: a top-level-only merge sees x.a defined locally, takes the local
    // {b: 1} whole, and resolves x.a.c to null.
    assertInputMapping(
        "{'x': {'a': {'b': 1, 'c': 2}}}",
        b -> b.zeebeInputExpression("x.a.b", "x.a.b").zeebeInputExpression("x.a.c", "x.a.c"),
        variable("x", "{'a':{'b':1,'c':2}}"));
  }

  @Test
  public void shouldCopyEveryFieldWhenNarrowingAnObjectOntoADifferentName() {
    // given: the control for the three above — renaming the target root means no shadowing is
    // involved at all, and this has always worked
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b -> b.zeebeInputExpression("x.a", "y.a").zeebeInputExpression("x.b", "y.b"),
        variable("y", "{'a':1,'b':2}"));
  }

  // ---------------------------------------------------------------------------------------------
  // Scenarios 7.3 and 7.4 — reading back a partially mapped object
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldFallThroughToTheAncestorWhenReadingAnObjectAFieldWasAddedTo() {
    // given: a.b adds a field the ancestor's a does not have. The local a holds only b — nothing
    // seeds it from the ancestor — but reading a layers that b over the ancestor's a.
    assertInputMapping(
        "{'a': {'z': 99}}",
        b -> b.zeebeInputExpression("1", "a.b").zeebeInputExpression("a", "c"),
        variable("a", "{'b':1}"),
        variable("c", "{'b':1,'z':99}"));
  }

  @Test
  public void shouldFallThroughToTheAncestorWhenReadingAnObjectAFieldWasOverriddenIn() {
    // given: x.a overrides a field the ancestor's x already has. The mapped key wins on the read,
    // the unmapped one falls through.
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b -> b.zeebeInputExpression("3", "x.a").zeebeInputExpression("x", "y"),
        variable("x", "{'a':3}"),
        variable("y", "{'a':3,'b':2}"));
  }

  @Test
  public void shouldFallThroughToTheAncestorWhenReadingAFieldThatOnlyTheAncestorHas() {
    // given: the read digs straight into a key the mappings never defined
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b -> b.zeebeInputExpression("3", "x.a").zeebeInputExpression("x.b", "y"),
        variable("x", "{'a':3}"),
        variable("y", "2"));
  }

  @Test
  public void shouldNotLetAFallThroughReadLeakIntoTheMappedVariable() {
    // given: a read of x sits between two mappings that build it. The layered value the read sees
    // must not become part of what is written — x keeps only the keys the mappings targeted, so the
    // ancestor's b is in y but not in x.
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b ->
            b.zeebeInputExpression("3", "x.a")
                .zeebeInputExpression("x", "y")
                .zeebeInputExpression("9", "x.c"),
        variable("x", "{'a':3,'c':9}"),
        variable("y", "{'a':3,'b':2}"));
  }

  @Test
  public void shouldTotallyShadowAtTheLevelAMappingAssignedWhole() {
    // given: x.a assigns the whole of a, so a's own nesting in the ancestor is gone — but x's
    // sibling key d, which no mapping touched, still falls through. Fall-through stops at the
    // level a mapping actually assigned.
    assertInputMapping(
        "{'x': {'a': {'b': 1}, 'd': 4}}",
        b -> b.zeebeInputExpression("5", "x.a").zeebeInputExpression("x", "y"),
        variable("x", "{'a':5}"),
        variable("y", "{'a':5,'d':4}"));
  }

  // ---------------------------------------------------------------------------------------------
  // Nothing to fall through to
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldNotFallThroughWhenTheAncestorValueIsNotAnObject() {
    // given: the ancestor's x is a scalar, so there are no keys to layer under the mapping
    assertInputMapping(
        "{'x': 5}",
        b -> b.zeebeInputExpression("1", "x.a").zeebeInputExpression("x", "y"),
        variable("x", "{'a':1}"),
        variable("y", "{'a':1}"));
  }

  @Test
  public void shouldNotFallThroughWhenNoAncestorScopeHasTheName() {
    // given: nothing anywhere up the chain is called x
    assertInputMapping(
        "{}",
        b -> b.zeebeInputExpression("1", "x.a").zeebeInputExpression("x", "y"),
        variable("x", "{'a':1}"),
        variable("y", "{'a':1}"));
  }

  @Test
  public void shouldNotFallThroughAgainAfterAMappingAssignedTheWholeName() {
    // given: mapping 1 shadows x totally; mapping 2 re-creates it as an object, which rule 6 says
    // is a FRESH object rather than a merge into the dropped scalar. The total shadow persists, so
    // the read sees only what mapping 2 put there.
    //
    // Derived: the artifact does not spell this shape out. It follows from rule 6.3's "fresh
    // object" plus rule 7.1's "an assigned target is authoritative" — a target that was once
    // assigned whole never becomes partially shadowed again.
    assertInputMapping(
        "{'x': {'a': 1, 'b': 2}}",
        b ->
            b.zeebeInputExpression("1", "x")
                .zeebeInputExpression("2", "x.a")
                .zeebeInputExpression("x", "y"),
        variable("x", "{'a':2}"),
        variable("y", "{'a':2}"));
  }

  @Test
  public void shouldNotFallThroughAgainAfterAMappingAssignedAWholeNestedLevel() {
    // given: the shape above, one level down. x.a is assigned whole and then re-created as a fresh
    // object by x.a.c, so a stays totally shadowed and the ancestor's a.b is gone — while x's
    // sibling d, which no mapping ever assigned, still falls through.
    //
    // Derived: the same reasoning as the test above, and the reason the total shadow has to be
    // tracked per path level rather than per root variable.
    assertInputMapping(
        "{'x': {'a': {'b': 1}, 'd': 4}}",
        b ->
            b.zeebeInputExpression("1", "x.a")
                .zeebeInputExpression("2", "x.a.c")
                .zeebeInputExpression("x", "y"),
        variable("x", "{'a':{'c':2}}"),
        variable("y", "{'a':{'c':2},'d':4}"));
  }

  // ---------------------------------------------------------------------------------------------
  // Scenario 7.5 — the fall-through stops at the nearest ancestor scope
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldFallThroughToTheNearestAncestorScopeOnly() {
    // given: an x on the process instance and a narrower x on the enclosing sub-process. Rule 2's
    // walk up the chain stops at the sub-process and takes its x whole, so partial shadowing layers
    // the mapping over that value only — the process instance's c never reaches y.
    final String processId = processId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .subProcess(
                    "enclosing",
                    enclosing -> {
                      enclosing.zeebeInputExpression("{a: 1, b: 2}", "x");
                      enclosing
                          .embeddedSubProcess()
                          .startEvent()
                          .subProcess(
                              MAPPED_ELEMENT_ID,
                              mapped -> {
                                mapped.embeddedSubProcess().startEvent().endEvent();
                                mapped
                                    .zeebeInputExpression("3", "x.a")
                                    .zeebeInputExpression("x", "y");
                              })
                          .endEvent();
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{'x': {'a': 1, 'b': 2, 'c': 3}}")
            .create();

    // then
    assertMappedElementVariables(
        processInstanceKey, variable("x", "{'a':3}"), variable("y", "{'a':3,'b':2}"));
  }

  @Test
  public void shouldFallThroughToTheMultiInstanceInputElementVariable() {
    // given: the input element variable is written to the inner activity's OWN scope while the
    // child is activating, before its input mappings run, so it is the value a nested target
    // partially shadows — the one shape where the fall-through resolves in the element's own scope
    // rather than an ancestor's
    final String processId = processId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    MAPPED_ELEMENT_ID,
                    t ->
                        t.zeebeJobType("test")
                            .zeebeInputExpression("3", "item.a")
                            .zeebeInputExpression("item", "y")
                            .multiInstance(
                                m ->
                                    m.zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{'items': [{'a': 1, 'b': 2}]}")
            .create();

    // then: the multi-instance body shares the inner activity's element id, so filter by type
    final long innerInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(MAPPED_ELEMENT_ID)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    // four variable records at the inner scope: item and loopCounter from the loop setup, then
    // item updated and y created by the input mapping merge. ELEMENT_ACTIVATED above is exported
    // after the merge, so all four are already present and limit(4) does not block.
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(innerInstanceKey)
                .limit(4))
        .extracting(Record::getValue)
        .extracting(v -> variable(v.getName(), v.getValue()))
        .contains(variable("y", "{'a':3,'b':2}"), variable("item", "{'a':3}"));
  }

  // ---------------------------------------------------------------------------------------------

  /**
   * Deploys a process whose single sub-process carries the given input mappings, runs an instance
   * with the given scope variables, and asserts the local variables the element ends up with — and
   * only those.
   */
  private void assertInputMapping(
      final String scopeVariables,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings,
      final VariableValue... expected) {
    assertMappedElementVariables(activate(scopeVariables, mappings), expected);
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
