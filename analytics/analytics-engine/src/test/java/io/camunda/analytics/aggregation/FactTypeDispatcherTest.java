/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.processor.ProcessorTopology;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FactTypeDispatcherTest {

  @Test
  void shouldDeliverAFactOnlyToTheChildrenOfItsType() {
    // given a dispatch node with one child per fact type
    final RecordingSink instances = new RecordingSink();
    final RecordingSink incidents = new RecordingSink();
    final FactTypeDispatcher dispatcher = new FactTypeDispatcher();
    final ProcessorTopology<Fact> topology =
        ProcessorTopology.<Fact>builder()
            .source("dispatch", dispatcher)
            .processor("instances", instances, "dispatch")
            .processor("incidents", incidents, "dispatch")
            .build();
    dispatcher.route("instances", FactType.PROCESS_INSTANCE);
    dispatcher.route("incidents", FactType.INCIDENT);
    topology.init();

    // when facts of both types flow through
    final Fact instance = fact(FactType.PROCESS_INSTANCE);
    final Fact incident = fact(FactType.INCIDENT);
    topology.process(instance);
    topology.process(incident);

    // then each child saw only its own type
    assertThat(instances.seen).containsExactly(instance);
    assertThat(incidents.seen).containsExactly(incident);
  }

  @Test
  void shouldDeliverToEveryChildRoutedForTheSameType() {
    // given two meters bound to the same fact type
    final RecordingSink first = new RecordingSink();
    final RecordingSink second = new RecordingSink();
    final FactTypeDispatcher dispatcher = new FactTypeDispatcher();
    final ProcessorTopology<Fact> topology =
        ProcessorTopology.<Fact>builder()
            .source("dispatch", dispatcher)
            .processor("first", first, "dispatch")
            .processor("second", second, "dispatch")
            .build();
    dispatcher.route("first", FactType.ELEMENT);
    dispatcher.route("second", FactType.ELEMENT);
    topology.init();

    // when a fact of that type flows through
    final Fact element = fact(FactType.ELEMENT);
    topology.process(element);

    // then both children received it, in wiring order
    assertThat(first.seen).containsExactly(element);
    assertThat(second.seen).containsExactly(element);
  }

  @Test
  void shouldBroadcastToAChildWithoutATypeConstraint() {
    // given a typed child and an untyped (broadcast) child
    final RecordingSink typed = new RecordingSink();
    final RecordingSink untyped = new RecordingSink();
    final FactTypeDispatcher dispatcher = new FactTypeDispatcher();
    final ProcessorTopology<Fact> topology =
        ProcessorTopology.<Fact>builder()
            .source("dispatch", dispatcher)
            .processor("typed", typed, "dispatch")
            .processor("untyped", untyped, "dispatch")
            .build();
    dispatcher.route("typed", FactType.PROCESS_DEFINITION);
    dispatcher.broadcast("untyped");
    topology.init();

    // when facts of several types flow through
    final Fact definition = fact(FactType.PROCESS_DEFINITION);
    final Fact instance = fact(FactType.PROCESS_INSTANCE);
    topology.process(definition);
    topology.process(instance);

    // then the typed child saw only its type; the broadcast child saw everything
    assertThat(typed.seen).containsExactly(definition);
    assertThat(untyped.seen).containsExactly(definition, instance);
  }

  @Test
  void shouldRebuildTheDispatchTableWithTheTopology() {
    // given a first-generation topology routing PROCESS_INSTANCE facts to one meter
    final RecordingSink oldMeter = new RecordingSink();
    final FactTypeDispatcher first = new FactTypeDispatcher();
    final ProcessorTopology<Fact> before =
        ProcessorTopology.<Fact>builder()
            .source("dispatch", first)
            .processor("old-meter", oldMeter, "dispatch")
            .build();
    first.route("old-meter", FactType.PROCESS_INSTANCE);
    before.init();
    before.process(fact(FactType.PROCESS_INSTANCE));

    // when the topology is rebuilt (a catalog reload): a fresh dispatcher wired from the current
    // node set, the old meter gone and a new incident meter added
    final RecordingSink newMeter = new RecordingSink();
    final FactTypeDispatcher second = new FactTypeDispatcher();
    final ProcessorTopology<Fact> after =
        ProcessorTopology.<Fact>builder()
            .source("dispatch", second)
            .processor("new-meter", newMeter, "dispatch")
            .build();
    second.route("new-meter", FactType.INCIDENT);
    after.init();
    after.process(fact(FactType.PROCESS_INSTANCE));
    after.process(fact(FactType.INCIDENT));

    // then the rebuilt dispatch routes only to the current nodes — no stale route survived
    assertThat(oldMeter.seen).hasSize(1);
    assertThat(newMeter.seen)
        .singleElement()
        .extracting(Fact::factType)
        .isEqualTo(FactType.INCIDENT);
  }

  private static Fact fact(final FactType type) {
    return Fact.builder(type).eventTime(1L).source(1, 1L).build();
  }

  private static final class RecordingSink implements Processor<Fact, Void> {

    private final List<Fact> seen = new ArrayList<>();

    @Override
    public void init(final ProcessorContext<Void> context) {}

    @Override
    public void process(final Fact fact) {
      seen.add(fact);
    }
  }
}
