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
import io.camunda.analytics.projection.applier.ElementActivatedApplier;
import io.camunda.analytics.projection.applier.ElementCompletedApplier;
import io.camunda.analytics.projection.applier.ElementEvictApplier;
import io.camunda.analytics.projection.applier.IncidentCreatedApplier;
import io.camunda.analytics.projection.applier.IncidentEvictApplier;
import io.camunda.analytics.projection.applier.IncidentResolvedApplier;
import io.camunda.analytics.projection.applier.VariableApplier;
import io.camunda.analytics.projection.derive.ElementActivatedDeriver;
import io.camunda.analytics.projection.derive.ElementCompletedDeriver;
import io.camunda.analytics.projection.derive.IncidentCreatedDeriver;
import io.camunda.analytics.projection.derive.IncidentResolvedDeriver;
import io.camunda.analytics.projection.derive.ProcessDeployedDeriver;
import io.camunda.analytics.projection.dispatch.RecordDispatch;
import io.camunda.analytics.projection.dispatch.RecordHandler;
import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.processor.PunctuationType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * The Model-A base projection as a stateful {@link Processor}: each source record is routed by its
 * {@code (ValueType, Intent)} through a {@link RecordDispatch} handler that runs the ordered steps
 * — {@code apply} (fold into a materialized row) → {@code derive} (read the updated row, forward
 * facts) → {@code evict} (drop terminal rows). The read/write split makes {@link
 * io.camunda.analytics.projection.applier.EventApplier appliers} the sole mutators and {@link
 * io.camunda.analytics.projection.derive.FactDeriver derivers} read-only, so a fact is a pure
 * projection of the rows and a new dataset is a forward-only subscription over the fact stream that
 * never touches this fold.
 *
 * <p>Rows are bounded by evict-after-emit for instances that complete, and by an event-time
 * straggler sweep for those that do not: a {@link PunctuationType#STREAM_TIME} punctuator evicts
 * rows whose {@code start + sla} deadline has passed as stream time advances.
 *
 * <p>Correctness rests on the runtime's consistent-cut checkpoint (Model F): the rows and the
 * consumed offset commit as one atomic cut, so replay-from-committed lands on matching state — the
 * projection therefore just materializes its stores plainly ({@link #checkpoint()}).
 */
public final class AnalyticsBaseProjection implements Processor<SourceRecord, Fact> {

  /**
   * Straggler eviction horizon: rows of instances that never complete are evicted this long (in
   * event time) after activation. Generous by default so live instances complete well within.
   */
  private static final Duration DEFAULT_SLA = Duration.ofDays(7);

  private final MutableProjectionState state;
  private final Duration sla;
  private RecordDispatch dispatch;

  public AnalyticsBaseProjection(final MutableProjectionState state) {
    this(state, DEFAULT_SLA);
  }

  public AnalyticsBaseProjection(final MutableProjectionState state, final Duration sla) {
    this.state = state;
    this.sla = sla;
  }

  @Override
  public void init(final ProcessorContext<Fact> context) {
    dispatch = wire(context::forward, sla.toMillis());
    // Straggler eviction: as stream time advances, evict rows whose SLA deadline has passed.
    context.schedule(
        sla,
        PunctuationType.STREAM_TIME,
        streamTime -> state.sweepExpiredDeadlines(streamTime, key -> {}));
  }

  /** Declares each applier/deriver with its collaborators, then registers the transition table. */
  private RecordDispatch wire(final Consumer<Fact> facts, final long slaMillis) {
    final ElementCompletedApplier completed =
        new ElementCompletedApplier(state, ElementStatus.COMPLETED);
    final ElementCompletedApplier terminated =
        new ElementCompletedApplier(state, ElementStatus.TERMINATED);
    final ElementEvictApplier elementEvict = new ElementEvictApplier(state, slaMillis);
    final IncidentEvictApplier incidentEvict = new IncidentEvictApplier(state);

    return new RecordDispatch()
        .on(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            RecordHandler.applyDerive(
                new ElementActivatedApplier(state, slaMillis), new ElementActivatedDeriver(facts)))
        .on(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            RecordHandler.applyDeriveEvict(
                completed,
                new ElementCompletedDeriver(state, facts, Transition.COMPLETED),
                elementEvict))
        .on(
            ValueType.PROCESS_INSTANCE,
            ProcessInstanceIntent.ELEMENT_TERMINATED,
            RecordHandler.applyDeriveEvict(
                terminated,
                new ElementCompletedDeriver(state, facts, Transition.TERMINATED),
                elementEvict))
        .onAnyIntent(ValueType.VARIABLE, RecordHandler.apply(new VariableApplier(state)))
        .on(
            ValueType.INCIDENT,
            IncidentIntent.CREATED,
            RecordHandler.applyDerive(
                new IncidentCreatedApplier(state), new IncidentCreatedDeriver(state, facts)))
        .on(
            ValueType.INCIDENT,
            IncidentIntent.RESOLVED,
            RecordHandler.applyDeriveEvict(
                new IncidentResolvedApplier(state),
                new IncidentResolvedDeriver(state, facts),
                incidentEvict))
        .on(
            ValueType.PROCESS,
            ProcessIntent.CREATED,
            RecordHandler.derive(new ProcessDeployedDeriver(facts)));
  }

  @Override
  public void process(final SourceRecord record) {
    dispatch.dispatch(record);
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
