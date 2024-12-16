/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.write.domain.Copyable;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public class UpsertMerger<T extends Copyable<T>> implements QueueItemMerger {

  private final ContextType contextType;
  private final Long key;
  private final Class<T> clazz;
  private final Function<ObjectBuilder<T>, ObjectBuilder<T>> mergeFunction;

  public UpsertMerger(
      final ContextType contextType,
      final Long key,
      final Class<T> clazz,
      final Function<? extends ObjectBuilder<T>, ? extends ObjectBuilder<T>> mergeFunction) {
    this.contextType = contextType;
    this.key = key;
    this.clazz = clazz;
    this.mergeFunction = (Function<ObjectBuilder<T>, ObjectBuilder<T>>) mergeFunction;
  }

  @Override
  public boolean canBeMerged(final QueueItem queueItem) {
    return queueItem.id().equals(key)
        && queueItem.contextType() == contextType
        && clazz.isInstance(queueItem.parameter());
  }

  @Override
  public QueueItem merge(final QueueItem originalItem) {
    return originalItem.copy(
        b -> b.parameter(((Copyable<T>) originalItem.parameter()).copy(mergeFunction)));
  }
}
