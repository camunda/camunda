/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.join;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.processor.ProcessorTopology;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The stream-table join enriches a stream of orders with the customer's tier held in a table store,
 * wired as a processor node whose downstream sink collects the joined results.
 */
final class StreamTableJoinTest {

  private record Order(String customerId, long amount) {}

  private record EnrichedOrder(String customerId, long amount, String tier) {}

  @Test
  void shouldEnrichMatchingRecordsAndDropMissesOnInnerJoin() {
    // given — a tier table with c1, c2 (c3 is absent)
    final KeyValueStore<DbString, DbString> table = tierTable();
    final Collecting<EnrichedOrder> sink = new Collecting<>();
    final ProcessorTopology<Order> topology =
        ProcessorTopology.<Order>builder()
            .source(
                "join",
                StreamTableJoin.inner(
                    "tiers",
                    new DbString(),
                    (order, key) -> key.wrapString(order.customerId()),
                    StreamTableJoinTest::enrich))
            .processor("sink", sink, "join")
            .addStateStore("tiers", table, "join")
            .build();

    // when
    topology.init();
    List.of(new Order("c1", 100), new Order("c3", 30), new Order("c2", 70))
        .forEach(topology::process);

    // then — only the matched orders were enriched and forwarded; c3 was dropped
    assertThat(sink.received)
        .containsExactly(
            new EnrichedOrder("c1", 100, "gold"), new EnrichedOrder("c2", 70, "silver"));
  }

  @Test
  void shouldForwardMissesWithNullOnLeftOuterJoin() {
    // given — the same table, but a left-outer join
    final KeyValueStore<DbString, DbString> table = tierTable();
    final Collecting<EnrichedOrder> sink = new Collecting<>();
    final ProcessorTopology<Order> topology =
        ProcessorTopology.<Order>builder()
            .source(
                "join",
                StreamTableJoin.leftOuter(
                    "tiers",
                    new DbString(),
                    (order, key) -> key.wrapString(order.customerId()),
                    StreamTableJoinTest::enrich))
            .processor("sink", sink, "join")
            .addStateStore("tiers", table, "join")
            .build();

    // when
    topology.init();
    List.of(new Order("c1", 100), new Order("c3", 30)).forEach(topology::process);

    // then — the miss is forwarded with the fallback tier
    assertThat(sink.received)
        .containsExactly(
            new EnrichedOrder("c1", 100, "gold"), new EnrichedOrder("c3", 30, "unknown"));
  }

  private static EnrichedOrder enrich(final Order order, final DbString tier) {
    return new EnrichedOrder(
        order.customerId(), order.amount(), tier == null ? "unknown" : tier.toString());
  }

  private static KeyValueStore<DbString, DbString> tierTable() {
    final KeyValueStore<DbString, DbString> table =
        new InMemoryKeyValueStore<>(new DbString(), new DbString());
    put(table, "c1", "gold");
    put(table, "c2", "silver");
    return table;
  }

  private static void put(
      final KeyValueStore<DbString, DbString> table, final String key, final String value) {
    final DbString k = new DbString();
    final DbString v = new DbString();
    k.wrapString(key);
    v.wrapString(value);
    table.put(k, v);
  }

  /** A terminal sink that records what it receives. */
  private static final class Collecting<T> implements Processor<T, Void> {
    private final List<T> received = new ArrayList<>();

    @Override
    public void process(final T record) {
      received.add(record);
    }
  }
}
