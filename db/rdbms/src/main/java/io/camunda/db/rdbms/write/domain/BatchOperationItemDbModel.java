/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.slf4j.LoggerFactory.getLogger;

import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.BatchOperationEntity;
import java.time.OffsetDateTime;
import org.slf4j.Logger;

public record BatchOperationItemDbModel(
    String batchOperationKey,
    long itemKey,
    long processInstanceKey,
    BatchOperationEntity.BatchOperationItemState state,
    OffsetDateTime processedDate,
    String errorMessage) {

  private static final Logger LOG = getLogger(BatchOperationItemDbModel.class);

  public BatchOperationItemDbModel truncateErrorMessage(
      final int sizeLimit, final Integer byteLimit) {
    if (errorMessage == null) {
      return this;
    }

    final var truncatedValue = TruncateUtil.truncateValue(errorMessage, sizeLimit, byteLimit);

    if (truncatedValue != null && truncatedValue.length() < errorMessage.length()) {
      LOG.warn(
          "Truncated error message for batchOperation {} item {}, original message was: {}",
          batchOperationKey,
          itemKey,
          errorMessage);
    }

    return new BatchOperationItemDbModel(
        batchOperationKey, itemKey, processInstanceKey, state, processedDate, truncatedValue);
  }
}
