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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A merger that can handle upsert operations on queue items where the parameter is a BatchInsertDto
 * containing a list of models. This merger scans the list to find a matching item by key and merges
 * the update into that item.
 *
 * @param <D> The type of the BatchInsertDto
 * @param <T> The type of the model contained in the list
 */
public class ListParameterUpsertMerger<T extends Copyable<T>> implements QueueItemMerger {

  private final ContextType contextType;
  private final long id;
  private final Function<T, Long> keyExtractor;
  private final Function<ObjectBuilder<T>, ObjectBuilder<T>> mergeFunction;

  public ListParameterUpsertMerger(
      final ContextType contextType,
      final long id,
      final Function<T, Long> keyExtractor,
      final Function<? extends ObjectBuilder<T>, ? extends ObjectBuilder<T>> mergeFunction) {
    this.contextType = contextType;
    this.id = id;
    this.keyExtractor = keyExtractor;
    this.mergeFunction = (Function<ObjectBuilder<T>, ObjectBuilder<T>>) mergeFunction;
  }

  @Override
  public boolean canBeMerged(final QueueItem queueItem) {
    if (queueItem.contextType() != contextType) {
      return false;
    }

    if (!(queueItem.parameter() instanceof BatchInsertDto)) {
      return false;
    }

    final BatchInsertDto<T> dto = (BatchInsertDto<T>) queueItem.parameter();
    final List<T> items = dto.dbModels();

    return items.stream().anyMatch(item -> keyExtractor.apply(item).equals(id));
  }

  @Override
  public QueueItem merge(final QueueItem originalItem) {
    final BatchInsertDto<T> originalDto = (BatchInsertDto<T>) originalItem.parameter();
    final List<T> originalItems = originalDto.dbModels();

    // Create a new list with the updated item
    final List<T> updatedItems = new ArrayList<>();
    for (final T item : originalItems) {
      if (keyExtractor.apply(item).equals(id)) {
        // Apply the merge function to update this item
        updatedItems.add(item.copy(mergeFunction));
      } else {
        updatedItems.add(item);
      }
    }

    // Use the copy function to create a new DTO with the updated list
    return originalItem.copy(b -> b.parameter(new BatchInsertDto<>(updatedItems)));
  }
}
