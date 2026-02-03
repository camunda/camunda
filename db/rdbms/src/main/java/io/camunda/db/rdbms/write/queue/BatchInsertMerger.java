/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

class BatchInsertMerger<M> implements QueueItemMerger {

  private final ContextType contextType;
  private final M model;
  private final int maxBatchSize;

  protected BatchInsertMerger(
      final ContextType contextType, final M model, final int maxBatchSize) {
    this.contextType = contextType;
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
        && queueItem.parameter() instanceof BatchInsertDto
        && ((BatchInsertDto<M>) queueItem.parameter()).dbModels().size() < maxBatchSize;
  }

  @Override
  public QueueItem merge(final QueueItem originalItem) {
    return originalItem.copy(
        b ->
            b.parameter(
                BatchInsertDto.class.cast(originalItem.parameter()).withAdditionalDbModel(model)));
  }
}
