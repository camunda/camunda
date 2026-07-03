/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@link RecordValue} flyweight for {@link DimensionKey}, using the same msgpack {@code
 * UnpackedObject} machinery as the per-metric key flyweights it replaces (e.g. {@code
 * RegionKeyValue}) — so {@code toBytes}/{@code fromBytes} come free from the {@code DbValue}
 * serialization. Because the field set is schema-driven rather than a fixed list of named
 * properties, values are stored as an ordered msgpack array (one {@link DimensionValue} per column,
 * in schema order). The bound {@link DimensionSchema} is supplied at construction and used to
 * rebuild the typed {@link DimensionKey} on read; the schema itself is not stored per cell, so keys
 * stay compact. One instance is mutable and reused — give each consumer its own.
 */
public final class DimensionKeyValue extends UnpackedObject implements RecordValue<DimensionKey> {

  private final ArrayProperty<DimensionValue> valuesProp =
      new ArrayProperty<>("v", DimensionValue::new);
  private final DimensionSchema schema;

  public DimensionKeyValue(final DimensionSchema schema) {
    super(1);
    this.schema = Objects.requireNonNull(schema, "schema");
    declareProperty(valuesProp);
  }

  @Override
  public DimensionKeyValue wrapValue(final DimensionKey key) {
    if (!schema.equals(key.schema())) {
      throw new IllegalArgumentException(
          "key schema " + key.schema() + " does not match flyweight schema " + schema);
    }
    reset();
    for (final Object value : key.values()) {
      valuesProp.add().set(value);
    }
    return this;
  }

  @Override
  public DimensionKey value() {
    final List<Object> values = new ArrayList<>(schema.size());
    for (final DimensionValue value : valuesProp) {
      values.add(value.value());
    }
    return DimensionKey.of(schema, values);
  }
}
