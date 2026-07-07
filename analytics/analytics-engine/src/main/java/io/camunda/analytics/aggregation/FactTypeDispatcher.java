/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link FactType}-indexed dispatch node between the base projection and its consumers: instead
 * of broadcasting every fact to every meter/table node (each re-gating on its own bound fact type,
 * O(facts × nodes) mostly-rejected checks), the routes are compiled once at topology install time
 * and a fact is forwarded only to the children whose declared fact type matches. Children routed
 * via {@link #broadcast(String)} receive every fact regardless of type.
 *
 * <p>Delivery order is the route registration order, which the stage wiring keeps equal to the
 * nodes' wiring order — so the same facts reach the same nodes in the same order as the previous
 * broadcast, minus the deliveries each node would have rejected by fact type anyway. Rebuilt (and
 * re-registered) on every topology install, so a catalog reload recompiles the dispatch table.
 */
public final class FactTypeDispatcher implements Processor<Fact, Fact> {

  // Per fact type, the child names to forward to, in registration (= wiring) order; broadcast
  // children appear in every type's list so their relative order is preserved.
  private final Map<FactType, List<String>> childrenByType = new EnumMap<>(FactType.class);

  private ProcessorContext<Fact> context;

  public FactTypeDispatcher() {
    for (final FactType type : FactType.values()) {
      childrenByType.put(type, new ArrayList<>());
    }
  }

  /** Routes facts of {@code factType} — and only those — to the named child. */
  public FactTypeDispatcher route(final String childName, final FactType factType) {
    Objects.requireNonNull(childName, "childName");
    Objects.requireNonNull(factType, "factType");
    childrenByType.get(factType).add(childName);
    return this;
  }

  /** Routes every fact, regardless of type, to the named child (no type constraint). */
  public FactTypeDispatcher broadcast(final String childName) {
    Objects.requireNonNull(childName, "childName");
    childrenByType.values().forEach(children -> children.add(childName));
    return this;
  }

  @Override
  public void init(final ProcessorContext<Fact> context) {
    this.context = context;
  }

  @Override
  public void process(final Fact fact) {
    for (final String child : childrenByType.get(fact.factType())) {
      context.forward(fact, child);
    }
  }
}
