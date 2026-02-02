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
 * @param <M> The type of the model contained in the list
 */
public class ListParameterUpsertMerger<M extends Copyable<M>> implements QueueItemMerger {

  private final ContextType contextType;
  private final long id;
  private final Function<M, Long> keyExtractor;
  private final Function<ObjectBuilder<M>, ObjectBuilder<M>> mergeFunction;

  public ListParameterUpsertMerger(
      final ContextType contextType,
      final long id,
      final Function<M, Long> keyExtractor,
      final Function<? extends ObjectBuilder<M>, ? extends ObjectBuilder<M>> mergeFunction) {
    this.contextType = contextType;
    this.id = id;
    this.keyExtractor = keyExtractor;
    this.mergeFunction = (Function<ObjectBuilder<M>, ObjectBuilder<M>>) mergeFunction;
  }

  @Override
  public boolean canBeMerged(final QueueItem queueItem) {
    if (queueItem.contextType() != contextType) {
      return false;
    }

    if (!(queueItem.parameter() instanceof BatchInsertDto)) {
      return false;
    }

    final BatchInsertDto<M> dto = (BatchInsertDto<M>) queueItem.parameter();
    final List<M> items = dto.dbModels();

    return items.stream().anyMatch(item -> keyExtractor.apply(item).equals(id));
  }

  @Override
  public QueueItem merge(final QueueItem originalItem) {
    final BatchInsertDto<M> originalDto = (BatchInsertDto<M>) originalItem.parameter();
    final List<M> originalItems = originalDto.dbModels();

    // Create a new list with the updated item
    final List<M> updatedItems = new ArrayList<>();
    for (final M item : originalItems) {
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
