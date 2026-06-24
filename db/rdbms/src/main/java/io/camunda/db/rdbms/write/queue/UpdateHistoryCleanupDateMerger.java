/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.sql.ProcessInstanceMapper.UpdateHistoryCleanupDateDto;
import io.camunda.util.ObjectBuilder;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Function;

public class UpdateHistoryCleanupDateMerger implements QueueItemMerger {

  public static final int MAX_IN_CLAUSE_SIZE = 1000;
  public static final int MAX_DATE_DIFFERENCE_MS = 1000;

  private final ContextType contextType;
  private final Function<
          ? super UpdateHistoryCleanupDateDto.Builder, ? super UpdateHistoryCleanupDateDto.Builder>
      mergeFunction;
  private final OffsetDateTime cleanupDate;

  public UpdateHistoryCleanupDateMerger(
      final ContextType contextType,
      final long processInstanceKey,
      final OffsetDateTime cleanupDate) {
    this.contextType = contextType;
    mergeFunction = builder -> builder.processInstanceKey(processInstanceKey);
    this.cleanupDate = cleanupDate;
  }

  @Override
  public boolean canBeMerged(final QueueItem queueItem) {
    return queueItem.contextType() == contextType
        && queueItem.parameter() instanceof UpdateHistoryCleanupDateDto
        && Math.abs(
                Duration.between(
                        ((UpdateHistoryCleanupDateDto) queueItem.parameter()).historyCleanupDate(),
                        cleanupDate)
                    .toMillis())
            < MAX_DATE_DIFFERENCE_MS
        && ((UpdateHistoryCleanupDateDto) queueItem.parameter()).processInstanceKeys().size()
            < MAX_IN_CLAUSE_SIZE;
  }

  @Override
  public QueueItem merge(final QueueItem originalItem) {
    return originalItem.copy(
        b ->
            b.parameter(
                ((UpdateHistoryCleanupDateDto) originalItem.parameter())
                    .copy(
                        (Function<
                                ObjectBuilder<UpdateHistoryCleanupDateDto>,
                                ObjectBuilder<UpdateHistoryCleanupDateDto>>)
                            mergeFunction)));
  }
}
