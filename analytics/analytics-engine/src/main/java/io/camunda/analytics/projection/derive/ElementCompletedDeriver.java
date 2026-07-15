/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.dimension.Utf8View;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.ElementEntity;
import io.camunda.analytics.state.VariableNames;
import io.camunda.analytics.state.immutable.ProjectionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits a completion fact as a pure projection of the finalized element row: it reads {@code start,
 * end, durationMs, hadIncident} off the row and binds a lazy view of the visible variable snapshot
 * (resolved on demand when a dataset groups/filters by a variable). Declared once per terminal
 * transition — {@link Transition#COMPLETED} and {@link Transition#TERMINATED}.
 *
 * <p>A terminal record whose row is missing loses its fact — a silent undercount before: it is
 * counted via {@link ProjectionMetrics#factDropped()} and logged with the record's Zeebe
 * coordinates.
 */
public final class ElementCompletedDeriver implements FactDeriver {

  private static final Logger LOG = LoggerFactory.getLogger(ElementCompletedDeriver.class);

  private final ProjectionState state;
  private final Consumer<Fact> facts;
  private final Transition transition;
  private final VariableNames variableNames;
  private final ProjectionMetrics metrics;

  public ElementCompletedDeriver(
      final ProjectionState state,
      final Consumer<Fact> facts,
      final Transition transition,
      final VariableNames variableNames,
      final ProjectionMetrics metrics) {
    this.state = state;
    this.facts = facts;
    this.transition = transition;
    this.variableNames = variableNames;
    this.metrics = metrics;
  }

  @Override
  public void derive(final SourceRecord source) {
    final long elementInstanceKey = source.record().getKey();
    final ElementEntity row = state.element(elementInstanceKey);
    if (row == null) {
      // no activation was folded (out of order / already evicted) — the fact is lost
      metrics.factDropped();
      LOG.warn(
          "Dropped a {} fact: no element row for key {} (zeebe partition {}, position {})",
          transition,
          elementInstanceKey,
          source.record().getPartitionId(),
          source.record().getPosition());
      return;
    }
    final ProcessInstanceRecord value = (ProcessInstanceRecord) source.record().getValue();
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;

    final Fact.Builder fact =
        ElementFacts.base(source, value, transition)
            .field("startTime", row.start())
            .field("endTime", row.end())
            .field("durationMs", row.durationMs())
            .field("hadIncident", row.hadIncident())
            // Lazy, collect-once, and name-targeted: resolved only if a dataset reads a var.*
            // dimension/filter, and then only the specific names any dataset needs (not the whole
            // scope), at most once across all cube-meters.
            .variables(() -> state.variables(elementInstanceKey, variableNames));
    if (isProcess) {
      // processInstanceKey is stamped uniformly by ElementFacts.base — never duplicated here.
      fact.field("completedNormally", transition == Transition.COMPLETED);
    } else {
      fact.field("elementId", Utf8View.copyOf(value.getElementIdBuffer()))
          .field("elementType", value.getBpmnElementType().name());
    }
    facts.accept(fact.build());
  }
}
