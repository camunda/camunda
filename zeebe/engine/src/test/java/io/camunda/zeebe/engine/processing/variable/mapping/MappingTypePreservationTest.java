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
 * <p>This is a JUnit 4 test class and stays one: {@link EngineRule} is a JUnit 4
 * {@code @ClassRule}, so {@code @ParameterizedTest} is unavailable and the JUnit 4 {@code
 * Parameterized} runner interacts badly with it. Every scenario below is therefore two explicit
 * {@code @Test} methods — once for input mappings, once for output mappings — sharing a private
 * helper per direction, matching {@link InputMappingPartialShadowingTest} next door.
 *
 * <p>Every scenario runs both directions because input and output mappings only share {@code
 * evaluateVariableMappingExpression} and {@code MappingResultBuilder.put} — a regression in one
 * would not necessarily show in the other.
 *
 * <p>Regression tests for <a href="https://github.com/camunda/camunda/issues/60011">#60011</a>,
 * input-mapping rule 8.
 */
public final class MappingTypePreservationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String MAPPED_ELEMENT_ID = "mapped";
  private static final String TASK_ELEMENT_ID = "task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final TestName testName = new TestName();

  // ---------------------------------------------------------------------------------------------
  // T1 — a duration written by one mapping is read as a duration by the next (8.1)
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepDurationTypeAcrossInputMappings() {
    // given: x is a duration; y reads a field that only the FEEL value has, not its MessagePack
    // (string) encoding
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("duration(\"P1DT2H\")", "x").zeebeInputExpression("x.days", "y"),
        variable("x", "\"P1DT2H\""),
        variable("y", "1"));
  }

  @Test
  public void shouldKeepDurationTypeAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("duration(\"P1DT2H\")", "x")
                .zeebeOutputExpression("x.days", "y"),
        variable("x", "\"P1DT2H\""),
        variable("y", "1"));
  }

  // ---------------------------------------------------------------------------------------------
  // T2 — control: a field read within a single mapping's own expression must not regress (8.3)
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldEvaluateDurationFieldWithinASingleInputMapping() {
    assertInputMapping(
        "{}", b -> b.zeebeInputExpression("duration(\"P1DT2H\").days", "y"), variable("y", "1"));
  }

  @Test
  public void shouldEvaluateDurationFieldWithinASingleOutputMapping() {
    assertOutputMapping(
        "{}", b -> b.zeebeOutputExpression("duration(\"P1DT2H\").days", "y"), variable("y", "1"));
  }

  // ---------------------------------------------------------------------------------------------
  // T3 — across *variables* the type must still be lost (8.2): this is the half of rule 8 that is
  // supposed to stay broken. If y ever comes back as 1 here, a value is leaking its type across
  // the variable-scope write, which is a bug in the fix, not an improvement to bless.
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldStillLoseDurationTypeAcrossInputVariables() {
    assertInputMapping(
        "{'d': \"P1DT2H\"}", b -> b.zeebeInputExpression("d.days", "y"), variable("y", "null"));
  }

  @Test
  public void shouldStillLoseDurationTypeAcrossOutputVariables() {
    assertOutputMapping(
        "{'d': \"P1DT2H\"}", b -> b.zeebeOutputExpression("d.days", "y"), variable("y", "null"));
  }

  // ---------------------------------------------------------------------------------------------
  // T4 — the type survives at a dotted target, not only a whole name
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepDurationTypeAtADottedInputTarget() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("duration(\"P1DT2H\")", "x.a")
                .zeebeInputExpression("x.a.days", "y"),
        variable("x", "{\"a\":\"P1DT2H\"}"),
        variable("y", "1"));
  }

  @Test
  public void shouldKeepDurationTypeAtADottedOutputTarget() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("duration(\"P1DT2H\")", "x.a")
                .zeebeOutputExpression("x.a.days", "y"),
        variable("x", "{\"a\":\"P1DT2H\"}"),
        variable("y", "1"));
  }

  // ---------------------------------------------------------------------------------------------
  // T5 — the type survives inside a list
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepDurationTypeInsideAListAcrossInputMappings() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("[duration(\"P1D\")]", "x")
                .zeebeInputExpression("x[1].days", "y"),
        variable("x", "[\"P1D\"]"),
        variable("y", "1"));
  }

  @Test
  public void shouldKeepDurationTypeInsideAListAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("[duration(\"P1D\")]", "x")
                .zeebeOutputExpression("x[1].days", "y"),
        variable("x", "[\"P1D\"]"),
        variable("y", "1"));
  }

  // ---------------------------------------------------------------------------------------------
  // T6 — the type survives inside a context
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepDurationTypeInsideAContextAcrossInputMappings() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("{d: duration(\"P1D\")}", "x")
                .zeebeInputExpression("x.d.days", "y"),
        variable("x", "{\"d\":\"P1D\"}"),
        variable("y", "1"));
  }

  @Test
  public void shouldKeepDurationTypeInsideAContextAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("{d: duration(\"P1D\")}", "x")
                .zeebeOutputExpression("x.d.days", "y"),
        variable("x", "{\"d\":\"P1D\"}"),
        variable("y", "1"));
  }

  // ---------------------------------------------------------------------------------------------
  // T7 — date
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepDateTypeAcrossInputMappings() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("date(\"2024-03-05\")", "x").zeebeInputExpression("x.year", "y"),
        variable("x", "\"2024-03-05\""),
        variable("y", "2024"));
  }

  @Test
  public void shouldKeepDateTypeAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("date(\"2024-03-05\")", "x")
                .zeebeOutputExpression("x.year", "y"),
        variable("x", "\"2024-03-05\""),
        variable("y", "2024"));
  }

  // ---------------------------------------------------------------------------------------------
  // T8 — time
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepTimeTypeAcrossInputMappings() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("time(\"12:30:00@Europe/Berlin\")", "x")
                .zeebeInputExpression("x.hour", "y"),
        variable("x", "\"12:30:00@Europe/Berlin\""),
        variable("y", "12"));
  }

  @Test
  public void shouldKeepTimeTypeAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("time(\"12:30:00@Europe/Berlin\")", "x")
                .zeebeOutputExpression("x.hour", "y"),
        variable("x", "\"12:30:00@Europe/Berlin\""),
        variable("y", "12"));
  }

  // ---------------------------------------------------------------------------------------------
  // T9 — date and time
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepDateTimeTypeAcrossInputMappings() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("date and time(\"2024-03-05T12:30:00\")", "x")
                .zeebeInputExpression("x.hour", "y"),
        variable("x", "\"2024-03-05T12:30:00\""),
        variable("y", "12"));
  }

  @Test
  public void shouldKeepDateTimeTypeAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("date and time(\"2024-03-05T12:30:00\")", "x")
                .zeebeOutputExpression("x.hour", "y"),
        variable("x", "\"2024-03-05T12:30:00\""),
        variable("y", "12"));
  }

  // ---------------------------------------------------------------------------------------------
  // T10 — year-month duration
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepYearMonthDurationTypeAcrossInputMappings() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("duration(\"P1Y2M\")", "x").zeebeInputExpression("x.years", "y"),
        variable("x", "\"P1Y2M\""),
        variable("y", "1"));
  }

  @Test
  public void shouldKeepYearMonthDurationTypeAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("duration(\"P1Y2M\")", "x")
                .zeebeOutputExpression("x.years", "y"),
        variable("x", "\"P1Y2M\""),
        variable("y", "1"));
  }

  // ---------------------------------------------------------------------------------------------
  // T11 — a function value newly survives between mappings where it used to arrive as null.
  //
  // Measured directly rather than guessed, per the design's own warning that the obvious
  // consuming expression might not demonstrate anything. Invoking the function positionally
  // (`x(1)`) turns out to be a dead end unrelated to this fix: it evaluates to null both before
  // and after, because feel-scala does not resolve a function invocation through a
  // VariableProvider-backed context the way it does through a native context literal — confirmed
  // with `{x: function(a) a + 1, y: x(1)}.y`, which *does* call it (returns 2) when x is bound
  // natively instead of through our EvaluationContext bridge. `is defined(x)` sidesteps that and
  // asks the question this rule is actually about: is x still a value at all, or has it silently
  // become null. Measured against cf439c1deb7 (parent of the fix) and this branch's tip:
  // before, x round-trips through MessagePack as nil and `is defined(x)` is false; after, x
  // carries the live FEEL function value across the mapping boundary and `is defined(x)` is true.
  // The stored x is nil either way — MessagePack still has no representation for a function —
  // which is why x itself is asserted as "null" in both directions here, unchanged from before.
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldKeepFunctionValueRecognizableAcrossInputMappings() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("function(a) a + 1", "x")
                .zeebeInputExpression("is defined(x)", "y"),
        variable("x", "null"),
        variable("y", "true"));
  }

  @Test
  public void shouldKeepFunctionValueRecognizableAcrossOutputMappings() {
    assertOutputMapping(
        "{}",
        b ->
            b.zeebeOutputExpression("function(a) a + 1", "x")
                .zeebeOutputExpression("is defined(x)", "y"),
        variable("x", "null"),
        variable("y", "true"));
  }

  // ---------------------------------------------------------------------------------------------
  // A read of a partially built level is a snapshot: a later write into the same level must not
  // appear in a value an earlier mapping already read. Pinned at the builder-unit level too, in
  // InputMappingResultBuilderTest, where it is cheapest to observe; this is the end-to-end
  // equivalent.
  // ---------------------------------------------------------------------------------------------

  @Test
  public void shouldNotLetALaterInputMappingWriteLeakIntoAnEarlierRead() {
    assertInputMapping(
        "{}",
        b ->
            b.zeebeInputExpression("1", "x.a")
                .zeebeInputExpression("x", "y")
                .zeebeInputExpression("2", "x.b"),
        variable("x", "{\"a\":1,\"b\":2}"),
        variable("y", "{\"a\":1}"));
  }

  // ---------------------------------------------------------------------------------------------

  /** Runs the given input mappings and asserts the mapped element's local variables. */
  private void assertInputMapping(
      final String initialVariables,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings,
      final VariableValue... expected) {
    assertMappedElementVariables(activateInput(initialVariables, mappings), expected);
  }

  /** Runs the given output mappings and asserts the variables written to the flow scope. */
  private void assertOutputMapping(
      final String initialVariables,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings,
      final VariableValue... expected) {
    assertFlowScopeVariables(activateOutput(initialVariables, mappings), expected);
  }

  /**
   * Deploys a process whose single sub-process carries the given input mappings, starts an instance
   * with the given variables, and returns its key.
   */
  private long activateInput(
      final String initialVariables,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings) {
    final String processId = processId();
    ENGINE.deployment().withXmlResource(processWithInputMappings(processId, mappings)).deploy();
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(initialVariables)
        .create();
  }

  /**
   * Deploys a process whose single sub-process carries the given output mappings, wraps a service
   * task so the sub-process has something to complete, starts an instance with the given variables,
   * completes that job, and returns the process instance key.
   */
  private long activateOutput(
      final String initialVariables,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings) {
    final String processId = processId();
    final String jobType = "job-" + processId;
    ENGINE
        .deployment()
        .withXmlResource(processWithOutputMappings(processId, jobType, mappings))
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(initialVariables)
            .create();
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();
    return processInstanceKey;
  }

  private static BpmnModelInstance processWithInputMappings(
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

  private static BpmnModelInstance processWithOutputMappings(
      final String processId,
      final String jobType,
      final Consumer<ZeebeVariablesMappingBuilder<SubProcessBuilder>> mappings) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .subProcess(
            MAPPED_ELEMENT_ID,
            b -> {
              b.embeddedSubProcess()
                  .startEvent()
                  .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(jobType))
                  .endEvent();
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

  /**
   * Asserts the variables an output mapping wrote to the flow scope — the process instance, since
   * the mapped sub-process sits directly under the process root. Skips past the inner task's
   * completion first, the same way {@link ActivityOutputMappingTest} does, so the mapped element's
   * own output-mapping writes are the only variable records collected.
   */
  private void assertFlowScopeVariables(
      final long processInstanceKey, final VariableValue... expected) {
    final long taskCompletedPosition =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .getFirst()
            .getPosition();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() > taskCompletedPosition)
                .withScopeKey(processInstanceKey)
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
