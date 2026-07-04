/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.applier.ElementApplier;
import io.camunda.analytics.projection.applier.IncidentApplier;
import io.camunda.analytics.projection.applier.VariableApplier;
import io.camunda.analytics.projection.derive.ElementDeriver;
import io.camunda.analytics.projection.derive.IncidentDeriver;
import io.camunda.analytics.projection.derive.ProcessDefinitionDeriver;
import io.camunda.analytics.projection.dispatch.RecordDispatch;
import io.camunda.analytics.state.ElementEntity;
import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.processor.PunctuationType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * The Model-A base projection as a stateful {@link Processor}: each source record is routed by its
 * {@code (ValueType, Intent)} through a {@link RecordDispatch} handler that folds it into a
 * materialized row (apply), derives facts from the now-updated row (derive), then evicts terminal
 * rows (evict). Facts are a pure projection of the rows — the read/write split makes appliers the
 * sole mutators and derivers read-only — so a new dataset is a forward-only subscription over the
 * emitted fact stream that never touches this fold.
 *
 * <p>The rows are bounded by evict-after-emit for instances that complete, and by an event-time
 * straggler sweep for those that do not: a {@link PunctuationType#STREAM_TIME} punctuator evicts
 * rows whose {@code start + sla} deadline has passed as stream time advances.
 *
 * <p>Correctness rests on the runtime's consistent-cut checkpoint (Model F): the rows and the
 * consumed offset commit as one atomic cut, so replay-from-committed lands on matching state — this
 * projection therefore just materializes its stores plainly ({@link #checkpoint()}), with no
 * bespoke recovery.
 */
public final class AnalyticsBaseProjection implements Processor<SourceRecord, Fact> {

  /**
   * Straggler eviction horizon: rows of instances that never complete are evicted this long (in
   * event time) after activation. Generous by default so live instances complete well within.
   */
  private static final Duration DEFAULT_SLA = Duration.ofDays(7);

  private final MutableProjectionState state;
  private final Duration sla;
  private final RecordDispatch dispatch;
  private ProcessorContext<Fact> context;

  public AnalyticsBaseProjection(final MutableProjectionState state) {
    this(state, DEFAULT_SLA);
  }

  public AnalyticsBaseProjection(final MutableProjectionState state, final Duration sla) {
    this.state = state;
    this.sla = sla;
    dispatch = wire(sla.toMillis());
  }

  private RecordDispatch wire(final long slaMillis) {
    final ElementApplier elementApplier = new ElementApplier(slaMillis);
    final VariableApplier variableApplier = new VariableApplier();
    final IncidentApplier incidentApplier = new IncidentApplier();
    final ElementDeriver elementDeriver = new ElementDeriver();
    final IncidentDeriver incidentDeriver = new IncidentDeriver();
    final ProcessDefinitionDeriver processDefinitionDeriver = new ProcessDefinitionDeriver();

    return new RecordDispatch()
        .on(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            (source, projection, facts) -> {
              final ProcessInstanceRecordValue value = processInstance(source);
              elementApplier.activate(
                  source.record().getKey(),
                  source.record().getTimestamp(),
                  value.getBpmnElementType() == BpmnElementType.PROCESS,
                  value.getFlowScopeKey(),
                  projection);
              elementDeriver.activated(source, value, facts);
            })
        .on(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            (source, projection, facts) ->
                complete(
                    source,
                    projection,
                    facts,
                    Transition.COMPLETED,
                    elementApplier,
                    elementDeriver))
        .on(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_TERMINATED,
            (source, projection, facts) ->
                complete(
                    source,
                    projection,
                    facts,
                    Transition.TERMINATED,
                    elementApplier,
                    elementDeriver))
        .onAnyIntent(
            ValueType.VARIABLE,
            (source, projection, facts) -> {
              final VariableRecordValue value = (VariableRecordValue) source.record().getValue();
              variableApplier.put(
                  value.getScopeKey(), value.getName(), value.getValue(), projection);
            })
        .on(
            ValueType.INCIDENT,
            IncidentIntent.CREATED,
            (source, projection, facts) -> {
              final IncidentRecordValue value = (IncidentRecordValue) source.record().getValue();
              final String errorType = IncidentDeriver.errorTypeOf(value);
              incidentApplier.created(
                  value.getElementInstanceKey(),
                  source.record().getTimestamp(),
                  errorType,
                  projection);
              incidentDeriver.created(source, value, errorType, facts);
            })
        .on(
            ValueType.INCIDENT,
            IncidentIntent.RESOLVED,
            (source, projection, facts) -> {
              final IncidentRecordValue value = (IncidentRecordValue) source.record().getValue();
              incidentApplier.resolved(
                  value.getElementInstanceKey(), source.record().getTimestamp(), projection);
              incidentDeriver.resolved(source, value, projection, facts);
              incidentApplier.evict(value.getElementInstanceKey(), projection);
            })
        .on(
            ValueType.PROCESS,
            ProcessIntent.CREATED,
            (source, projection, facts) -> {
              if (source.record().getValue() instanceof final Process process) {
                processDefinitionDeriver.deployed(source, process, facts);
              }
            });
  }

  /** apply → derive → evict for an element/process terminal transition. */
  private static void complete(
      final SourceRecord source,
      final MutableProjectionState projection,
      final Consumer<Fact> facts,
      final Transition transition,
      final ElementApplier elementApplier,
      final ElementDeriver elementDeriver) {
    final ProcessInstanceRecordValue value = processInstance(source);
    final long elementInstanceKey = source.record().getKey();
    final ElementStatus status =
        transition == Transition.COMPLETED ? ElementStatus.COMPLETED : ElementStatus.TERMINATED;
    elementApplier.complete(elementInstanceKey, source.record().getTimestamp(), status, projection);
    final ElementEntity row = projection.element(elementInstanceKey);
    if (row == null) {
      return; // no activation was folded — nothing to derive or evict
    }
    final long start = row.start();
    elementDeriver.completed(source, value, projection, transition, facts);
    elementApplier.evict(elementInstanceKey, start, projection);
  }

  private static ProcessInstanceRecordValue processInstance(final SourceRecord source) {
    return (ProcessInstanceRecordValue) source.record().getValue();
  }

  @Override
  public void init(final ProcessorContext<Fact> context) {
    this.context = context;
    // Straggler eviction: as stream time advances, evict rows whose SLA deadline has passed.
    context.schedule(
        sla,
        PunctuationType.STREAM_TIME,
        streamTime -> state.sweepExpiredDeadlines(streamTime, key -> {}));
  }

  @Override
  public void process(final SourceRecord record) {
    dispatch.dispatch(record, state, context::forward);
  }

  @Override
  public void checkpoint() {
    state.checkpoint();
  }

  @Override
  public boolean needsCheckpoint() {
    return state.needsCheckpoint();
  }
}
