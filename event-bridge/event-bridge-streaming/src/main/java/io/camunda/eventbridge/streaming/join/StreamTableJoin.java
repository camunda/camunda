/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.join;

import io.camunda.eventbridge.streaming.processor.Processor;
import io.camunda.eventbridge.streaming.processor.ProcessorContext;
import io.camunda.eventbridge.streaming.state.api.ReadOnlyKeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Enriches a stream with a keyed lookup against a table held in a state store: for each stream
 * record it derives a join key, reads the matching table value, and forwards the {@link ValueJoiner
 * joined} result. The table side is whatever is materialized into the named store — another
 * processor keeping it up to date, or a static reference dataset — so this operator only reads.
 *
 * <p>Two modes: an <em>inner</em> join forwards only when the table has a match; a
 * <em>left-outer</em> join always forwards, passing {@code null} to the joiner on a miss. A joiner
 * returning {@code null} drops the record.
 *
 * <p>Single-writer, like every processor. The owned key flyweight and the store's returned value
 * flyweight are reused across records, so the join key is written and the value read within a
 * single {@link #process} call; nothing escapes it except the immutable joined result.
 *
 * @param <In> the stream record type
 * @param <K> the table key type (a {@link DbKey} flyweight)
 * @param <V> the table value type (a {@link DbValue} flyweight)
 * @param <Out> the joined result type forwarded downstream
 */
public final class StreamTableJoin<In, K extends DbKey, V extends DbValue, Out>
    implements Processor<In, Out> {

  private final String storeName;
  private final K keyFlyweight;
  private final BiConsumer<In, K> keyWriter;
  private final ValueJoiner<In, V, Out> joiner;
  private final boolean leftOuter;

  private ProcessorContext<Out> context;
  private ReadOnlyKeyValueStore<K, V> table;

  private StreamTableJoin(
      final String storeName,
      final K keyFlyweight,
      final BiConsumer<In, K> keyWriter,
      final ValueJoiner<In, V, Out> joiner,
      final boolean leftOuter) {
    this.storeName = storeName;
    this.keyFlyweight = keyFlyweight;
    this.keyWriter = keyWriter;
    this.joiner = joiner;
    this.leftOuter = leftOuter;
  }

  /**
   * An inner join: forwards {@code joiner(record, match)} only for records whose join key is
   * present in the table.
   *
   * @param storeName the state store wired to this processor holding the table
   * @param keyFlyweight the reusable key instance the join key is written into
   * @param keyWriter writes a record's join key into the key flyweight
   * @param joiner combines the record with the matched table value
   */
  public static <In, K extends DbKey, V extends DbValue, Out> StreamTableJoin<In, K, V, Out> inner(
      final String storeName,
      final K keyFlyweight,
      final BiConsumer<In, K> keyWriter,
      final ValueJoiner<In, V, Out> joiner) {
    return new StreamTableJoin<>(storeName, keyFlyweight, keyWriter, joiner, false);
  }

  /**
   * A left-outer join: forwards for every record, passing {@code null} to the joiner when the join
   * key is absent from the table.
   */
  public static <In, K extends DbKey, V extends DbValue, Out>
      StreamTableJoin<In, K, V, Out> leftOuter(
          final String storeName,
          final K keyFlyweight,
          final BiConsumer<In, K> keyWriter,
          final ValueJoiner<In, V, Out> joiner) {
    return new StreamTableJoin<>(storeName, keyFlyweight, keyWriter, joiner, true);
  }

  @Override
  public void init(final ProcessorContext<Out> context) {
    this.context = context;
    table = context.getStateStore(storeName);
  }

  @Override
  public void process(final In record) {
    keyWriter.accept(record, keyFlyweight);
    final Optional<V> match = table.get(keyFlyweight);
    if (match.isPresent()) {
      forward(joiner.join(record, match.get()));
    } else if (leftOuter) {
      forward(joiner.join(record, null));
    }
  }

  private void forward(final Out result) {
    if (result != null) {
      context.forward(result);
    }
  }
}
