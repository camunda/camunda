/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationErrorEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import java.util.Collections;
import org.apache.commons.lang3.StringUtils;

public class BatchOperationEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.operation.BatchOperationEntity, BatchOperationEntity> {

  @Override
  public BatchOperationEntity apply(
      final io.camunda.webapps.schema.entities.operation.BatchOperationEntity source) {
    if (source == null) {
      return null;
    }

    return isLegacy(source) ? mapLegacyBatchOperation(source) : mapBatchOperation(source);
  }

  /**
   * Checks if the batch operation is legacy or not. This is done be checking if the ID is NOT
   * numeric. New batch operations use a Long value, old ones use a UUID
   *
   * @param source the batch operation entity
   * @return true if the batch operation is legacy, false otherwise
   */
  private static boolean isLegacy(
      final io.camunda.webapps.schema.entities.operation.BatchOperationEntity source) {
    return !StringUtils.isNumeric(source.getId());
  }

  private BatchOperationEntity mapBatchOperation(
      final io.camunda.webapps.schema.entities.operation.BatchOperationEntity source) {
    return new BatchOperationEntity(
        source.getId(),
        BatchOperationState.valueOf(source.getState().name()),
        BatchOperationType.valueOf(source.getType().name()),
        source.getStartDate(),
        source.getEndDate(),
        source.getOperationsTotalCount(),
        source.getOperationsFailedCount(),
        source.getOperationsCompletedCount(),
        source.getErrors().stream().map(this::mapBatchOperationError).toList());
  }

  private BatchOperationErrorEntity mapBatchOperationError(
      final io.camunda.webapps.schema.entities.operation.BatchOperationErrorEntity source) {
    return new BatchOperationErrorEntity(
        source.getPartitionId(), source.getType(), source.getMessage());
  }

  private BatchOperationEntity mapLegacyBatchOperation(
      final io.camunda.webapps.schema.entities.operation.BatchOperationEntity source) {
    return new BatchOperationEntity(
        source.getId(),
        source.getState() != null
            ? BatchOperationState.valueOf(source.getState().name())
            : interpolateLegacyState(source),
        source.getType() != null ? BatchOperationType.valueOf(source.getType().name()) : null,
        source.getStartDate(),
        source.getEndDate(),
        source.getOperationsTotalCount(),
        0,
        source.getOperationsFinishedCount(),
        Collections.emptyList());
  }

  private BatchOperationState interpolateLegacyState(
      final io.camunda.webapps.schema.entities.operation.BatchOperationEntity entity) {
    if (entity.getEndDate() != null) {
      return BatchOperationState.COMPLETED;
    } else if (entity.getOperationsFinishedCount() == 0) {
      return BatchOperationState.CREATED;
    } else {
      return BatchOperationState.ACTIVE;
    }
  }
}
