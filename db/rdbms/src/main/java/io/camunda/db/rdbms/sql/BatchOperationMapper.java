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
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface BatchOperationMapper {

  Optional<BatchOperationDbModel> findById(String batchOperationId);

  void insert(BatchOperationDbModel batchOperationDbModel);

  void insertItems(BatchOperationItemsDto items);

  void updateCompleted(BatchOperationUpdateDto dto);

  void updateItemsWithState(BatchOperationItemStatusUpdateDto dto);

  void incrementOperationsTotalCount(BatchOperationUpdateCountDto dto);

  void incrementFailedOperationsCount(BatchOperationUpdateCountDto dto);

  void incrementCompletedOperationsCount(BatchOperationUpdateCountDto dto);

  Long count(BatchOperationDbQuery query);

  List<BatchOperationDbModel> search(BatchOperationDbQuery query);

  Long countItems(BatchOperationItemDbQuery query);

  List<BatchOperationItemEntity> searchItems(BatchOperationItemDbQuery query);

  record BatchOperationUpdateDto(
      String batchOperationId, BatchOperationState state, OffsetDateTime endDate) {}

  record BatchOperationUpdateCountDto(String batchOperationId, int count) {}

  record BatchOperationItemsDto(String batchOperationId, List<BatchOperationItemDbModel> items) {}

  record BatchOperationItemDto(
      String batchOperationId,
      Long itemKey,
      BatchOperationEntity.BatchOperationItemState state,
      OffsetDateTime processedDate,
      String errorMessage) {}

  record BatchOperationItemStatusUpdateDto(
      String batchOperationId,
      BatchOperationEntity.BatchOperationItemState oldState,
      BatchOperationEntity.BatchOperationItemState newState) {}
}
