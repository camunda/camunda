/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** How the queue items of one {@link ContextType} are ordered within a flush. */
public enum QueueOrdering {

  /**
   * Sorts by {@link WriteStatementType} (natural order), then {@link QueueItem#order()} (default
   * {@code 0}), then {@link QueueItem#statementId()}, so that items with the same statement ID can
   * be batched. The {@code order} field lets an individual item be placed before or after the
   * others it would otherwise tie with, without changing how any other item is sorted.
   */
  SORT_BY_STATEMENT_TYPE {
    @Override
    List<QueueItem> apply(final List<QueueItem> items) {
      final var sorted = new ArrayList<>(items);
      sorted.sort(
          Comparator.comparing(QueueItem::statementType)
              .thenComparing(QueueItem::order)
              .thenComparing(QueueItem::statementId));
      return sorted;
    }
  },

  /** Keeps the order in which the items were queued. */
  PRESERVE {
    @Override
    List<QueueItem> apply(final List<QueueItem> items) {
      return items;
    }
  },

  /**
   * Keeps the order of the items of each {@link QueueItem#id()}, and batches items of different
   * ids. Puts the n-th item of each id into round n and sorts by round, then by statement ID. Only
   * valid if every item only touches the rows of its own id.
   */
  PRESERVE_PER_ID {
    @Override
    List<QueueItem> apply(final List<QueueItem> items) {
      final Map<Object, Integer> itemsSeenPerId = new HashMap<>();
      final Map<QueueItem, Integer> roundByItem = new IdentityHashMap<>();
      for (final var item : items) {
        roundByItem.put(item, itemsSeenPerId.merge(item.id(), 1, Integer::sum));
      }
      final var sorted = new ArrayList<>(items);
      sorted.sort(
          Comparator.comparing((QueueItem item) -> roundByItem.get(item))
              .thenComparing(QueueItem::statementId));
      return sorted;
    }
  };

  abstract List<QueueItem> apply(List<QueueItem> items);
}
