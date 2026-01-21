/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import java.util.function.Function;

class BatchInsertMerger<T extends BatchInsertDto<T, M>, M> implements QueueItemMerger {

  private final ContextType contextType;
  private final Class<T> dtoClass;
  private final Function<T, Integer> listSizeExtractor;
  private final M model;
  private final int maxBatchSize;

  protected BatchInsertMerger(
      final ContextType contextType,
      final Class<T> dtoClass,
      final M model,
      final Function<T, Integer> listSizeExtractor,
      final int maxBatchSize) {
    this.contextType = contextType;
    this.dtoClass = dtoClass;
    this.listSizeExtractor = listSizeExtractor;
    this.model = model;
    this.maxBatchSize = maxBatchSize;
  }

  @Override
  public boolean canBeMerged(final QueueItem queueItem) {
    if (maxBatchSize == 1) {
      return false;
    }
    return queueItem.contextType() == contextType
        && queueItem.statementType() == WriteStatementType.INSERT
        && dtoClass.isInstance(queueItem.parameter())
        && listSizeExtractor.apply(dtoClass.cast(queueItem.parameter())) < maxBatchSize;
  }

  @Override
  public QueueItem merge(final QueueItem originalItem) {
    return originalItem.copy(
        b -> b.parameter(dtoClass.cast(originalItem.parameter()).withAdditionalDbModel(model)));
  }
}
