/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import io.camunda.eventbridge.streaming.aggregate.KeySelector;
import java.util.Objects;

/**
 * Builds the {@link DimensionKey} a fact groups under by reading each column of a {@link
 * DimensionSchema} from the fact's {@link FactRow} by name and encoding it straight into a reusable
 * {@link DimensionKeyWire} encoder — no intermediate list, no boxing copies, no {@code String}
 * materialization for string-typed fields (ADR 0008). This is the generic replacement for the
 * per-metric {@code KeySelector} lambdas (e.g. {@code Metrics::definitionKey}) — the grain is data
 * (the schema), not code.
 *
 * <p>Deterministic, as {@link KeySelector} requires: a fact always yields the same key. A dimension
 * absent from the row is {@code null} (the "unknown" bucket). Narrowing numeric coercions ({@code
 * Integer}/{@code Long} → the column's declared type) are applied so a fact that exposes, say, an
 * {@code int} version for a {@code LONG} column still binds; anything else is rejected with the
 * same strictness as {@link DimensionKey#of}.
 *
 * <p><b>Single-writer.</b> The encoder scratch and the probe key are reused across calls, so an
 * instance must not be shared between concurrently folding owners — construct one per aggregation,
 * like the operators themselves. {@link #probeKey} returns a reusable view over the scratch buffer
 * (valid only until the next call, for map lookups); {@link #ownKey} copies it into a storable key.
 */
public final class DimensionKeySelector implements KeySelector<FactRow, DimensionKey> {

  private final DimensionSchema schema;
  private final DimensionKeyWire wire = new DimensionKeyWire();
  private final DimensionKey probe;

  public DimensionKeySelector(final DimensionSchema schema) {
    this.schema = Objects.requireNonNull(schema, "schema");
    probe = DimensionKey.view(schema);
  }

  public DimensionSchema schema() {
    return schema;
  }

  @Override
  public DimensionKey getKey(final FactRow fact) {
    encode(fact);
    return DimensionKey.fromEncoded(schema, wire.copyBytes());
  }

  @Override
  public DimensionKey probeKey(final FactRow fact) {
    encode(fact);
    probe.wrapView(wire.array(), 0, wire.length());
    return probe;
  }

  @Override
  public DimensionKey ownKey(final DimensionKey key) {
    return key == probe ? key.toOwned() : key;
  }

  private void encode(final FactRow fact) {
    Objects.requireNonNull(fact, "fact");
    wire.begin(schema.size());
    for (int i = 0; i < schema.size(); i++) {
      final DimensionColumn column = schema.column(i);
      final Object value = fact.get(column.name());
      if (value == null) {
        wire.addNull();
        continue;
      }
      switch (column.type()) {
        case STRING, TEXT -> {
          if (value instanceof final String string) {
            wire.addString(string);
          } else {
            throw DimensionKey.typeMismatch(column, value);
          }
        }
        case LONG -> {
          if (value instanceof final Number number) {
            wire.addLong(number.longValue());
          } else {
            throw DimensionKey.typeMismatch(column, value);
          }
        }
        case INT -> {
          if (value instanceof final Number number) {
            wire.addInt(number.intValue());
          } else {
            throw DimensionKey.typeMismatch(column, value);
          }
        }
        case BOOLEAN -> {
          if (value instanceof final Boolean bool) {
            wire.addBoolean(bool);
          } else {
            throw DimensionKey.typeMismatch(column, value);
          }
        }
      }
    }
  }
}
