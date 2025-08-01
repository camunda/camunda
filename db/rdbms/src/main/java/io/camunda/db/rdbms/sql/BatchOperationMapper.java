/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.BatchOperationDbQuery;
import io.camunda.db.rdbms.read.domain.BatchOperationItemDbQuery;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface BatchOperationMapper extends HistoryCleanupMapper {

  void insert(BatchOperationDbModel batchOperationDbModel);

  void insertItems(BatchOperationItemsDto items);

  void insertErrors(BatchOperationErrorsDto error);

  void updateCompleted(BatchOperationUpdateDto dto);

  void updateItemsWithState(BatchOperationItemStatusUpdateDto dto);

  void incrementOperationsTotalCount(BatchOperationUpdateTotalCountDto dto);

  void incrementFailedOperationsCount(String batchOperationKey);

  void incrementCompletedOperationsCount(String batchOperationKey);

  Long count(BatchOperationDbQuery query);

  List<BatchOperationDbModel> search(BatchOperationDbQuery query);

  Long countItems(BatchOperationItemDbQuery query);

  List<BatchOperationItemEntity> searchItems(BatchOperationItemDbQuery query);

  int updateHistoryCleanupDate(UpdateHistoryCleanupDateDto dto);

  int cleanupHistory(CleanupBatchOperationHistoryDto dto);

  int cleanupItemHistory(CleanupBatchOperationHistoryDto dto);

  record CleanupBatchOperationHistoryDto(OffsetDateTime cleanupDate, int limit) {}

  record UpdateHistoryCleanupDateDto(String batchOperationKey, OffsetDateTime historyCleanupDate) {}

  record BatchOperationUpdateDto(
      String batchOperationKey, BatchOperationState state, OffsetDateTime endDate) {}

  record BatchOperationUpdateTotalCountDto(String batchOperationKey, int operationsTotalCount) {}

  record BatchOperationUpdateCountsDto(String batchOperationKey, long itemKey) {}

  record BatchOperationItemsDto(String batchOperationKey, List<BatchOperationItemDbModel> items) {}

  record BatchOperationItemDto(
      String batchOperationKey,
      Long itemKey,
      BatchOperationEntity.BatchOperationItemState state,
      OffsetDateTime processedDate,
      String errorMessage) {}

  record BatchOperationItemStatusUpdateDto(
      String batchOperationKey,
      BatchOperationEntity.BatchOperationItemState oldState,
      BatchOperationEntity.BatchOperationItemState newState) {}

  record BatchOperationErrorsDto(String batchOperationKey, List<BatchOperationErrorDto> errors) {}

  record BatchOperationErrorDto(Integer partitionId, String type, String message) {
    public static final Logger LOG = LoggerFactory.getLogger(BatchOperationErrorDto.class);

    public BatchOperationErrorDto truncateErrorMessage(
        final int sizeLimit, final Integer byteLimit) {
      if (message == null) {
        return this;
      }

      final var truncatedValue = TruncateUtil.truncateValue(message, sizeLimit, byteLimit);

      if (truncatedValue.length() < message.length()) {
        LOG.warn(
            "Truncated error message for batch operation partition {}, original message was: {}",
            partitionId,
            message);
      }

      return new BatchOperationErrorDto(partitionId, type, truncatedValue);
    }
  }
}
