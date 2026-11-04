/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery.NO_ELEMENT_INSTANCE;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import java.util.function.ObjLongConsumer;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.MutableLong;

/**
 * Scans subscriptions keyed by element instance in chunks, for the pending subscription recovery.
 */
final class PendingSubscriptionScan {

  private PendingSubscriptionScan() {}

  /**
   * Visits the subscriptions of one chunk, see {@link
   * io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery#recoverPending(long, int)}.
   *
   * @param startKey a key that sorts before every subscription of its element instance; its element
   *     instance key is overwritten
   * @param visitor called with each subscription and its element instance key
   * @return the element instance key to resume from, or {@code NO_ELEMENT_INSTANCE} when done
   */
  static <K extends DbKey, V extends DbValue> long scanChunk(
      final ColumnFamily<DbCompositeKey<DbLong, K>, V> columnFamily,
      final DbCompositeKey<DbLong, K> startKey,
      final long fromElementInstanceKey,
      final int batchLimit,
      final ObjLongConsumer<V> visitor) {
    final var currentElementInstanceKey = new MutableLong(NO_ELEMENT_INSTANCE);
    final var resumeAt = new MutableLong(NO_ELEMENT_INSTANCE);
    final var visited = new MutableInteger();

    startKey.first().wrapLong(fromElementInstanceKey);
    columnFamily.whileTrue(
        fromElementInstanceKey == NO_ELEMENT_INSTANCE ? null : startKey,
        (key, value) -> {
          final long elementInstanceKey = key.first().getValue();
          if (elementInstanceKey != currentElementInstanceKey.get()) {
            if (currentElementInstanceKey.get() != NO_ELEMENT_INSTANCE
                && visited.get() >= batchLimit) {
              resumeAt.set(elementInstanceKey);
              return false;
            }
            currentElementInstanceKey.set(elementInstanceKey);
          }
          visited.increment();
          visitor.accept(value, elementInstanceKey);
          return true;
        });
    return resumeAt.get();
  }
}
