/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.typed;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Flyweight-typed, read-only access to one store of a {@link ReadOnlyView} — the typed counterpart
 * of {@link LayeredColumnFamily} for asynchronous readers. Reads reflect the frozen cut the view
 * was built from; staleness relative to the owner store is the contract (see {@link ReadOnlyView}).
 *
 * <p><b>Threading:</b> a reader typically runs on a different thread than the owner store, which is
 * fine — everything reachable from the view is immutable. The reader itself, however, owns the
 * mutable key/value flyweights passed at construction: one reader instance must not be shared
 * across threads, and returned or visited flyweights alias the reader's instances — copy before
 * storing.
 */
public final class LayeredViewReader<KeyType extends DbKey, ValueType extends DbValue> {

  private final ReadOnlyView view;
  private final String storeName;
  private final KeyType keyInstance;
  private final ValueType valueInstance;

  public LayeredViewReader(
      final ReadOnlyView view,
      final String storeName,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.view = Objects.requireNonNull(view, "view");
    this.storeName = Objects.requireNonNull(storeName, "storeName");
    this.keyInstance = Objects.requireNonNull(keyInstance, "keyInstance");
    this.valueInstance = Objects.requireNonNull(valueInstance, "valueInstance");
  }

  /** The visible value for {@code key} wrapped into this reader's value instance, or null. */
  public ValueType get(final KeyType key) {
    final byte[] valueBytes = view.get(storeName, TypedBytes.serialize(key));
    if (valueBytes == null) {
      return null;
    }
    return TypedBytes.wrapInto(valueInstance, valueBytes);
  }

  public boolean exists(final KeyType key) {
    return view.exists(storeName, TypedBytes.serialize(key));
  }

  /**
   * Visits every visible entry whose key starts with {@code keyPrefix}, in unsigned-byte key order.
   * Key and value flyweights are re-wrapped per entry — copy before storing.
   */
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    view.prefixScan(
        storeName,
        TypedBytes.serialize(keyPrefix),
        (keyBytes, valueBytes) ->
            visitor.accept(
                TypedBytes.wrapInto(keyInstance, keyBytes),
                TypedBytes.wrapInto(valueInstance, valueBytes)));
  }
}
