/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.StateBackedProjectionState;
import io.camunda.analytics.state.VariableNames;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.processor.PunctuationType;
import io.camunda.eventbridge.streaming.processor.Punctuator;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class AnalyticsBaseProjectionTest {

  private static final long PI_KEY = 123L;
  private static final long TASK_KEY = 456L;

  private final StateBackedProjectionState state = StateBackedProjectionState.inMemory();
  private final CapturingContext context = new CapturingContext();
  private final CountingMetrics metrics = new CountingMetrics();
  private final AnalyticsBaseProjection projection =
      new AnalyticsBaseProjection(state, VariableNames.of(Set.of("region")), metrics);

  AnalyticsBaseProjectionTest() {
    projection.init(context);
  }

  private Fact only(final FactType type, final Transition transition) {
    final List<Fact> matches =
        context.facts.stream()
            .filter(f -> f.factType() == type && transition.name().equals(f.get(Fact.TRANSITION)))
            .toList();
    assertThat(matches).as("facts of %s/%s", type, transition).hasSize(1);
    return matches.get(0);
  }

  @Test
  void shouldEmitActivatedAndCompletedInstanceFacts() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L));

    assertThat(only(FactType.PROCESS_INSTANCE, Transition.ACTIVATED).get("durationMs")).isNull();
    final Fact completed = only(FactType.PROCESS_INSTANCE, Transition.COMPLETED);
    assertThat(completed.get("durationMs")).isEqualTo(500L);
    assertThat(completed.get("startTime")).isEqualTo(1000L);
    assertThat(completed.get("endTime")).isEqualTo(1500L);
    assertThat(completed.get("completedNormally")).isEqualTo(true);
    assertThat(completed.get("hadIncident")).isEqualTo(false);
    assertThat(completed.get("bpmnProcessId")).isEqualTo(Utf8View.of("order"));
    assertThat(completed.eventTime()).isEqualTo(1500L);
  }

  @Test
  void shouldTagTerminationDistinctly() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_TERMINATED, 1200L, 11L));

    final Fact terminated = only(FactType.PROCESS_INSTANCE, Transition.TERMINATED);
    assertThat(terminated.get("durationMs")).isEqualTo(200L);
    assertThat(terminated.get("completedNormally")).isEqualTo(false);
  }

  @Test
  void shouldMaterializeTheRowThenEvictItAfterEmit() {
    // given an activated process instance, the row is queryable in its own right (Model A)
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    assertThat(state.element(PI_KEY)).isNotNull();
    assertThat(state.element(PI_KEY).status()).isEqualTo(ElementStatus.ACTIVE);
    assertThat(state.element(PI_KEY).start()).isEqualTo(1000L);

    // when it completes
    projection.process(variable(PI_KEY, "region", "EU", 11L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    // then the terminal row and its variables are evicted after the fact is derived
    assertThat(state.element(PI_KEY)).as("row evicted after emit").isNull();
    assertThat(state.variables(PI_KEY)).as("variables cleared with the scope").isEmpty();
  }

  @Test
  void shouldEnrichCompletionWithNamespacedVariable() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(variable(PI_KEY, "region", "EU", 11L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    assertThat(only(FactType.PROCESS_INSTANCE, Transition.COMPLETED).get("var.region"))
        .isEqualTo(Utf8View.of("EU"));
  }

  @Test
  void shouldResolveVariablesUpTheScopeHierarchy() {
    // given a process-scoped variable and a task nested in that process instance
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(variable(PI_KEY, "region", "EU", 11L));
    projection.process(task(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1100L, 12L));
    // the task has no local variable, but resolves the process-instance-scoped one up its parent
    // (flow) scope chain — the engine's variable visibility
    assertThat(state.variables(TASK_KEY))
        .as("resolved up the scope hierarchy")
        .containsEntry("region", Utf8View.of("EU"));

    // when the task completes
    projection.process(task(ProcessInstanceIntent.ELEMENT_COMPLETED, 1300L, 13L));

    // then its completion fact carries the process-instance-scoped variable
    assertThat(only(FactType.ELEMENT, Transition.COMPLETED).get("var.region"))
        .isEqualTo(Utf8View.of("EU"));
  }

  @Test
  void shouldStampBusinessValueOnInstanceFacts() {
    // given the start-payload 'amount' variable folded before the process activates (the
    // creation command persists variables first, so this mirrors the real record order)
    projection.process(numericVariable(PI_KEY, "amount", "4200", 9L));

    // when the instance activates and completes
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L));

    // then the activation carries +value and the completion −value (the in-flight level nets to
    // zero), with the positive value on both for the throughput sum
    final Fact activated = only(FactType.PROCESS_INSTANCE, Transition.ACTIVATED);
    assertThat(activated.get("value")).isEqualTo(4200L);
    assertThat(activated.get("valueDelta")).isEqualTo(4200L);
    final Fact completed = only(FactType.PROCESS_INSTANCE, Transition.COMPLETED);
    assertThat(completed.get("value")).isEqualTo(4200L);
    assertThat(completed.get("valueDelta")).isEqualTo(-4200L);
  }

  @Test
  void shouldBalanceTheValueDeltaOnTermination() {
    // given a valued instance
    projection.process(numericVariable(PI_KEY, "amount", "150", 9L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));

    // when it terminates instead of completing
    projection.process(process(ProcessInstanceIntent.ELEMENT_TERMINATED, 1200L, 11L));

    // then the termination also carries −value — terminated work leaves the in-flight level too
    final Fact terminated = only(FactType.PROCESS_INSTANCE, Transition.TERMINATED);
    assertThat(terminated.get("valueDelta")).isEqualTo(-150L);
  }

  @Test
  void shouldOmitBusinessValueWhenAbsentOrNonNumeric() {
    // given one instance without the value variable and one with a non-numeric value
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L));

    // then no value fields are stamped — absence keeps the fact out of the value meters via
    // their implicit NOT_NULL(measure) filters, instead of folding a phantom 0
    assertThat(only(FactType.PROCESS_INSTANCE, Transition.ACTIVATED).get("value")).isNull();
    final Fact completed = only(FactType.PROCESS_INSTANCE, Transition.COMPLETED);
    assertThat(completed.get("value")).isNull();
    assertThat(completed.get("valueDelta")).isNull();

    // and a non-numeric amount is ignored the same way
    context.facts.clear();
    projection.process(variable(PI_KEY, "amount", "not-a-number", 12L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 2000L, 13L));
    assertThat(only(FactType.PROCESS_INSTANCE, Transition.ACTIVATED).get("value")).isNull();
  }

  @Test
  void shouldStampTheVariantSignatureOnTheInstanceEndFact() {
    // given an instance whose retry loop activates one task twice (distinct element instances)
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(element(400L, "register", ProcessInstanceIntent.ELEMENT_ACTIVATED, 11L));
    projection.process(element(400L, "register", ProcessInstanceIntent.ELEMENT_COMPLETED, 12L));
    projection.process(element(401L, "assess", ProcessInstanceIntent.ELEMENT_ACTIVATED, 13L));
    projection.process(element(401L, "assess", ProcessInstanceIntent.ELEMENT_COMPLETED, 14L));
    projection.process(element(402L, "assess", ProcessInstanceIntent.ELEMENT_ACTIVATED, 15L));
    projection.process(element(402L, "assess", ProcessInstanceIntent.ELEMENT_COMPLETED, 16L));

    // when the instance completes
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 2000L, 17L));

    // then the end fact carries the signature and the canonical (sorted, bucketed) element list
    final Fact completed = only(FactType.PROCESS_INSTANCE, Transition.COMPLETED);
    assertThat(completed.get("variantHash")).isNotNull();
    assertThat(completed.get("variantElements")).isEqualTo("assess×2-3, register");
    // and the accumulator is evicted with the instance
    final List<String> leftover = new ArrayList<>();
    state.forEachVariantElement(PI_KEY, (elementId, count) -> leftover.add(elementId));
    assertThat(leftover).as("accumulator cleared on instance evict").isEmpty();
  }

  @Test
  void shouldFoldArrivalOrderAndReplayToTheSameVariantHash() {
    // given the same two-branch element set folded in two different interleavings (a parallel
    // gateway's branches have no deterministic activation order), plus an exact replay
    final long forward = variantHashOf(List.of("dispatch", "notify"));
    final long interleaved = variantHashOf(List.of("notify", "dispatch"));
    final long replayed = variantHashOf(List.of("dispatch", "notify"));

    // then every fold lands on ONE variant
    assertThat(interleaved).isEqualTo(forward);
    assertThat(replayed).isEqualTo(forward);
  }

  @Test
  void shouldGiveATerminatedInstanceItsOwnPartialSetVariant() {
    // given a completed instance that executed both elements
    final long full = variantHashOf(List.of("register", "payout"));

    // and an instance terminated mid-way, after only the first element (fresh fixture)
    final StateBackedProjectionState terminatedState = StateBackedProjectionState.inMemory();
    final CapturingContext terminatedContext = new CapturingContext();
    final AnalyticsBaseProjection terminated =
        new AnalyticsBaseProjection(terminatedState, VariableNames.NONE, new CountingMetrics());
    terminated.init(terminatedContext);
    terminated.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    terminated.process(element(500L, "register", ProcessInstanceIntent.ELEMENT_ACTIVATED, 11L));
    terminated.process(element(500L, "register", ProcessInstanceIntent.ELEMENT_TERMINATED, 12L));
    terminated.process(process(ProcessInstanceIntent.ELEMENT_TERMINATED, 1500L, 13L));

    // then the termination fact carries a variant of its own — the partial element set
    final Fact fact =
        terminatedContext.facts.stream()
            .filter(
                f ->
                    f.factType() == FactType.PROCESS_INSTANCE
                        && Transition.TERMINATED.name().equals(f.get(Fact.TRANSITION)))
            .findFirst()
            .orElseThrow();
    assertThat(fact.get("variantHash")).isNotNull();
    assertThat(fact.get("variantHash")).isNotEqualTo(full);
    assertThat(fact.get("variantElements")).isEqualTo("register");
  }

  /**
   * Folds a full instance (activation, one pass over each element, completion) through a fresh
   * projection and returns the end fact's variant hash — the arrival order is the list order.
   */
  private static long variantHashOf(final List<String> elementIds) {
    final StateBackedProjectionState state = StateBackedProjectionState.inMemory();
    final CapturingContext context = new CapturingContext();
    final AnalyticsBaseProjection projection =
        new AnalyticsBaseProjection(state, VariableNames.NONE, new CountingMetrics());
    projection.init(context);
    long position = 10L;
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, position++));
    long elementKey = 600L;
    for (final String elementId : elementIds) {
      projection.process(
          element(elementKey, elementId, ProcessInstanceIntent.ELEMENT_ACTIVATED, position++));
      projection.process(
          element(elementKey, elementId, ProcessInstanceIntent.ELEMENT_COMPLETED, position++));
      elementKey++;
    }
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 2000L, position));
    return context.facts.stream()
        .filter(
            f ->
                f.factType() == FactType.PROCESS_INSTANCE
                    && Transition.COMPLETED.name().equals(f.get(Fact.TRANSITION)))
        .map(f -> (Long) f.get("variantHash"))
        .findFirst()
        .orElseThrow();
  }

  private static SourceRecord element(
      final long elementInstanceKey,
      final String elementId,
      final ProcessInstanceIntent intent,
      final long position) {
    return processInstance(
        elementInstanceKey,
        PI_KEY,
        intent,
        BpmnElementType.SERVICE_TASK,
        elementId,
        position * 100,
        position);
  }

  @Test
  void shouldStampHadIncidentOnTheElementItOccurredOn() {
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(incident(IncidentIntent.CREATED, PI_KEY, 1100L, 11L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 12L));

    assertThat(only(FactType.PROCESS_INSTANCE, Transition.COMPLETED).get("hadIncident"))
        .isEqualTo(true);
  }

  @Test
  void shouldCountTheMissingRowAndTheDroppedFactForAnUnknownCompletion() {
    // given no activation was folded for the element instance

    // when its completion arrives anyway (an ordering-invariant breach, ADR 0007)
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 10L));

    // then the fold's missing row and the derivation's lost fact are both counted — and no fact
    // is emitted (the formerly silent undercount, now observable)
    assertThat(metrics.foldRowMissing).isEqualTo(1);
    assertThat(metrics.factDropped).isEqualTo(1);
    assertThat(context.facts).isEmpty();
  }

  @Test
  void shouldKeepTheAlarmCountersAtZeroOnAHealthyFlow() {
    // given / when an ordered activation and completion
    projection.process(process(ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, 10L));
    projection.process(process(ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, 11L));

    // then no fold met a missing row and no fact was dropped
    assertThat(metrics.foldRowMissing).isZero();
    assertThat(metrics.factDropped).isZero();
  }

  @Test
  void shouldDeriveIncidentResolutionDurationFromTheRow() {
    projection.process(incident(IncidentIntent.CREATED, TASK_KEY, 1000L, 10L));
    projection.process(incident(IncidentIntent.RESOLVED, TASK_KEY, 1700L, 11L));

    final Fact created = only(FactType.INCIDENT, Transition.CREATED);
    assertThat(created.get("delta")).isEqualTo(1L);
    assertThat(created.get("errorType")).isEqualTo("JOB_NO_RETRIES");
    final Fact resolved = only(FactType.INCIDENT, Transition.RESOLVED);
    assertThat(resolved.get("delta")).isEqualTo(-1L);
    assertThat(resolved.get("durationMs")).isEqualTo(700L);
    // the incident type is read from the materialized row (Model A), not just the resolve record
    assertThat(resolved.get("errorType")).isEqualTo("JOB_NO_RETRIES");
    // the incident row is evicted after resolution
    assertThat(state.incident(TASK_KEY)).isNull();
  }

  private static SourceRecord process(
      final ProcessInstanceIntent intent, final long timestamp, final long position) {
    return processInstance(
        PI_KEY, -1L, intent, BpmnElementType.PROCESS, "order", timestamp, position);
  }

  private static SourceRecord task(
      final ProcessInstanceIntent intent, final long timestamp, final long position) {
    return processInstance(
        TASK_KEY, PI_KEY, intent, BpmnElementType.SERVICE_TASK, "reviewOrder", timestamp, position);
  }

  private static SourceRecord processInstance(
      final long elementInstanceKey,
      final long flowScopeKey,
      final ProcessInstanceIntent intent,
      final BpmnElementType elementType,
      final String elementId,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PI_KEY)
            .setProcessDefinitionKey(77L)
            .setBpmnProcessId("order")
            .setVersion(3)
            .setTenantId("<default>")
            .setElementId(elementId)
            .setFlowScopeKey(flowScopeKey)
            .setBpmnElementType(elementType);
    return record(
        value, ValueType.PROCESS_INSTANCE, intent, elementInstanceKey, position, timestamp);
  }

  private static SourceRecord variable(
      final long scopeKey, final String name, final String value, final long position) {
    return variableRecord(scopeKey, name, "\"" + value + "\"", position);
  }

  /** A msgpack-number variable (e.g. the 'amount' start payload), not a string. */
  private static SourceRecord numericVariable(
      final long scopeKey, final String name, final String number, final long position) {
    return variableRecord(scopeKey, name, number, position);
  }

  private static SourceRecord variableRecord(
      final long scopeKey, final String name, final String json, final long position) {
    final VariableRecord variable =
        new VariableRecord()
            .setProcessInstanceKey(PI_KEY)
            .setScopeKey(scopeKey)
            .setName(BufferUtil.wrapString(name))
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(json)));
    return record(
        variable, ValueType.VARIABLE, VariableIntent.CREATED, scopeKey, position, position);
  }

  private static SourceRecord incident(
      final IncidentIntent intent,
      final long elementInstanceKey,
      final long timestamp,
      final long position) {
    final IncidentRecord incident =
        new IncidentRecord()
            .setProcessInstanceKey(PI_KEY)
            .setElementInstanceKey(elementInstanceKey)
            .setBpmnProcessId(BufferUtil.wrapString("order"))
            .setElementId(BufferUtil.wrapString("reviewOrder"))
            .setTenantId("<default>")
            .setErrorType(ErrorType.JOB_NO_RETRIES);
    return record(incident, ValueType.INCIDENT, intent, position, position, timestamp);
  }

  private static SourceRecord record(
      final UnifiedRecordValue value,
      final ValueType valueType,
      final Intent intent,
      final long key,
      final long position,
      final long timestamp) {
    final RecordMetadata metadata =
        new RecordMetadata().recordType(RecordType.EVENT).valueType(valueType).intent(intent);
    final Record<?> record =
        new CopiedRecord<>(value, metadata, key, 1, position, position - 1, timestamp);
    return new SourceRecord(1, position, record);
  }

  /** A {@link ProjectionMetrics} fake counting each signal. */
  private static final class CountingMetrics implements ProjectionMetrics {

    private int duplicateSkipped;
    private int foldRowMissing;
    private int factDropped;

    @Override
    public void duplicateSkipped() {
      duplicateSkipped++;
    }

    @Override
    public void foldRowMissing() {
      foldRowMissing++;
    }

    @Override
    public void factDropped() {
      factDropped++;
    }
  }

  /** A {@link ProcessorContext} that captures the forwarded (materialized) facts. */
  private static final class CapturingContext implements ProcessorContext<Fact> {

    private final List<Fact> facts = new ArrayList<>();

    @Override
    public void forward(final Fact value) {
      // Materialize now — while the projection is still live (before evict) — mirroring how a
      // downstream aggregate resolves a fact's variables at fold time, so post-hoc assertions see
      // the resolved snapshot.
      facts.add(value.materialize());
    }

    @Override
    public void forward(final Fact value, final String childName) {
      facts.add(value.materialize());
    }

    @Override
    public void schedule(
        final Duration interval, final PunctuationType type, final Punctuator punctuator) {
      // no punctuation used by the base projection
    }

    @Override
    public <S> S getStateStore(final String name) {
      throw new UnsupportedOperationException();
    }
  }
}
