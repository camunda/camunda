/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

import java.time.Duration;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;

/**
 * Builds the whole pipeline as a single Kafka Streams {@link Topology}, mixing the Processor API
 * (Stage A) with the DSL (Stages B/C).
 *
 * <p>Read this top to bottom as the map of the pipeline:
 *
 * <pre>
 *   source topic ──(keyed by instanceKey)
 *        │
 *   [Stage A]  process(BaseProjectionProcessor) + KeyValueStore  → CompletionFact
 *        │
 *   [Stage B]  filter: forward-only gate (sourceOffset >= activationOffset)
 *        │
 *   selectKey(processId)                         ← declares the SHUFFLE
 *        │
 *   groupByKey → windowedBy(1 min, no grace) → aggregate(count+sum)   [Stage C]
 *        │            └── repartition topic is created here (the physical shuffle)
 *   mapValues → Cell
 *        │
 *   foreach(ServingSink)                          ← sink stub
 * </pre>
 */
public final class AnalyticsTopology {

  public static final String SOURCE_TOPIC = "process-events";
  static final String INSTANCE_STORE = "instance-state-store";
  static final String AGGREGATE_STORE = "completion-count-avg-store";

  private final long activationOffset;

  /**
   * @param activationOffset the forward-only gate threshold: only facts derived from a source record
   *     at or beyond this offset are aggregated. This is the exactly-once "start-from" boundary a
   *     hand-rolled runtime would enforce with segment-origin coordinates; in KS it is a plain
   *     filter on the coordinate we carried through.
   */
  public AnalyticsTopology(final long activationOffset) {
    this.activationOffset = activationOffset;
  }

  public Topology build() {
    final StreamsBuilder builder = new StreamsBuilder();

    // --- Serdes (explicit, always) ------------------------------------------------------------
    final Serde<Long> instanceKeySerde = Serdes.Long();
    final Serde<SourceEvent> sourceSerde = JsonSerde.of(SourceEvent.class);
    final Serde<CompletionFact> factSerde = JsonSerde.of(CompletionFact.class);
    final Serde<String> processIdSerde = Serdes.String();
    final Serde<DurationAggregate> aggSerde = JsonSerde.of(DurationAggregate.class);

    // --- State store for Stage A --------------------------------------------------------------
    // We declare the store and attach it to the processor. Kafka Streams owns everything else about
    // it: a local RocksDB instance per task, a compacted CHANGELOG topic that mirrors every write,
    // and full restore of that store from the changelog after a crash or rebalance. That changelog is
    // exactly the "hand-rolled changelog" you would otherwise build — here it is free.
    final KeyValueBytesStoreSupplier storeSupplier = Stores.persistentKeyValueStore(INSTANCE_STORE);
    final StoreBuilder<KeyValueStore<Long, InstanceState>> instanceStore =
        Stores.keyValueStoreBuilder(
            storeSupplier, instanceKeySerde, JsonSerde.of(InstanceState.class));
    builder.addStateStore(instanceStore);

    // --- Stage A: base projection (Processor API) ---------------------------------------------
    // The source stream is keyed by instanceKey, so every event for an instance is co-located on one
    // task with one store shard — the precondition for the fold to be correct.
    final KStream<Long, CompletionFact> facts =
        builder
            .stream(SOURCE_TOPIC, Consumed.with(instanceKeySerde, sourceSerde))
            .process(() -> new BaseProjectionProcessor(INSTANCE_STORE), INSTANCE_STORE);

    // --- Stage B: dataset subscription + forward-only gate ------------------------------------
    // The single declared dataset is "completed instances, grouped by processId, tumbling 1-minute
    // windows, metric = count + avg(durationMs)". The subscription is: keep real completions and apply
    // the forward-only gate on the source offset. (Breach facts carry offset -1 and are always kept —
    // they are fresh emissions, not replays of a source record.)
    final KStream<Long, CompletionFact> gated =
        facts.filter(
            (instanceKey, fact) ->
                fact.slaBreached() || fact.sourceOffset() >= activationOffset);
    // NOTE ON THE GATE: this is a simplified single-threshold gate. A precise forward-only gate is
    // PER SOURCE PARTITION (offset >= activationOffset[fact.sourcePartition()]), because offsets are
    // only monotonic within a partition. The shape is identical; only the lookup differs.

    // --- Shuffle: repartition by processId ----------------------------------------------------
    // selectKey changes the key from instanceKey to processId. Because the downstream groupByKey then
    // needs co-partitioning on the new key, Kafka Streams inserts a REPARTITION TOPIC here — that
    // topic IS the shuffle. It is created and managed for us; we only asked for a different key.
    final KStream<String, CompletionFact> byProcess =
        gated.selectKey((instanceKey, fact) -> fact.processId());

    // --- Stage C: windowed global aggregate ---------------------------------------------------
    // In idiomatic Kafka Streams the "local partial" and "global merge" of a classic map-reduce
    // collapse into a SINGLE windowed aggregate that runs after the repartition. There is no separate
    // combine step exposed in the DSL (that is a Flink/Spark distinction); the accumulator is kept
    // associative (count + sum, not avg) so the fold is correct no matter the arrival order.
    //
    // ofSizeWithNoGrace(1 min): tumbling event-time windows, zero grace period → late records past
    // the window are dropped rather than updating a closed window. Event time comes from the record
    // timestamp we preserved in the processor's context.forward.
    final KTable<Windowed<String>, DurationAggregate> aggregated =
        byProcess
            .groupByKey(Grouped.with(processIdSerde, factSerde))
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
            .aggregate(
                DurationAggregate::empty,
                (processId, fact, acc) -> acc.add(fact),
                Materialized.<String, DurationAggregate, WindowStore<Bytes, byte[]>>as(
                        AGGREGATE_STORE)
                    .withKeySerde(processIdSerde)
                    .withValueSerde(aggSerde));

    // --- Project to the serving Cell and sink -------------------------------------------------
    // toStream turns the changelog of the windowed KTable into a stream of updates. We compute the
    // average at the very end (from count+sum) and flatten the Windowed key into the Cell.
    //
    // (If we instead wrote the windowed KTable out to a topic, the key would need an explicit
    // windowed serde, e.g.
    //   WindowedSerdes.timeWindowedSerdeFrom(String.class, Duration.ofMinutes(1).toMillis());
    // a plain String serde will not round-trip a Windowed<String> key.)
    aggregated
        .toStream()
        .map(
            (windowedKey, agg) -> {
              final String processId = windowedKey.key();
              final long windowStartMs = windowedKey.window().start();
              final Cell cell = new Cell(processId, windowStartMs, agg.count(), agg.average());
              return KeyValue.pair(processId, cell);
            })
        .foreach(new ServingSink());

    return builder.build();
  }
}
