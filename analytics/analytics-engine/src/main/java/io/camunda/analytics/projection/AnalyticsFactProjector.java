/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.eventbridge.streaming.fold.Projector;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Map;

/**
 * The base-projection fold, expressed as a registry: each consumed record is routed by its {@link
 * ValueType} to the one {@link FactDeriver} registered for it, which folds the record into the
 * shared {@link BaseProjectionStore} and emits uniform, {@link FactType}-tagged, transition-tagged
 * {@link Fact}s. The registry <em>is</em> the capability list — what the engine projects is read
 * off the table below, not off a chain of type/intent branches.
 *
 * <p>The derived fact set is deliberately small and canonical; specialized cohort metrics (SLA,
 * no-incident) are expressed as dataset declarations over these facts rather than as bespoke fold
 * outputs. The fold is a deterministic function of the in-order, per-partition source stream and
 * keeps all its mutable state in the {@link BaseProjectionStore}, so re-folding the source rebuilds
 * the projection exactly (start-from-offset recovery, no changelog).
 */
public final class AnalyticsFactProjector implements Projector<SourceRecord, Fact> {

  private final BaseProjectionStore store;
  private final Map<ValueType, FactDeriver> derivers;

  public AnalyticsFactProjector(final BaseProjectionStore store) {
    this.store = store;
    derivers =
        Map.of(
            ValueType.VARIABLE, new VariableDeriver(),
            ValueType.PROCESS, new ProcessDefinitionDeriver(),
            ValueType.INCIDENT, new IncidentDeriver(),
            ValueType.PROCESS_INSTANCE, new ElementDeriver());
  }

  @Override
  public void apply(final SourceRecord sourceRecord, final Collector<Fact> out) {
    final FactDeriver deriver = derivers.get(sourceRecord.record().getValueType());
    if (deriver != null) {
      deriver.derive(sourceRecord, store, out);
    }
  }

  @Override
  public void checkpoint() {
    store.checkpoint();
  }

  @Override
  public boolean needsCheckpoint() {
    return store.needsCheckpoint();
  }
}
