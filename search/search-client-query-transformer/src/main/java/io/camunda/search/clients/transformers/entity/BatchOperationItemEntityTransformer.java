/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.webapps.schema.entities.operation.OperationState;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

public class BatchOperationItemEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.operation.OperationEntity, BatchOperationItemEntity> {

  @Override
  public BatchOperationItemEntity apply(
      final io.camunda.webapps.schema.entities.operation.OperationEntity source) {
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
      final io.camunda.webapps.schema.entities.operation.OperationEntity source) {
    return !StringUtils.isNumeric(source.getBatchOperationId());
  }

  private BatchOperationItemEntity mapBatchOperation(
      final io.camunda.webapps.schema.entities.operation.OperationEntity source) {
    return new BatchOperationItemEntity(
        source.getBatchOperationId(),
        source.getItemKey(),
        source.getProcessInstanceKey(),
        map(source.getState()),
        source.getCompletedDate(),
        source.getErrorMessage());
  }

  private BatchOperationItemEntity mapLegacyBatchOperation(
      final io.camunda.webapps.schema.entities.operation.OperationEntity source) {
    return new BatchOperationItemEntity(
        source.getBatchOperationId(),
        // in legacy items only one of the following is set (processInstanceKey as default)
        ObjectUtils.firstNonNull(source.getIncidentKey(), source.getProcessInstanceKey()),
        source.getProcessInstanceKey(),
        map(source.getState()),
        source.getCompletedDate(),
        source.getErrorMessage());
  }

  private BatchOperationItemState map(final OperationState state) {
    if (state == null) {
      return null;
    }
    return switch (state) {
      case SCHEDULED, LOCKED, SENT -> BatchOperationItemState.ACTIVE;
      case COMPLETED -> BatchOperationItemState.COMPLETED;
      case FAILED -> BatchOperationItemState.FAILED;
      case SKIPPED -> BatchOperationItemState.SKIPPED;
    };
  }
}
