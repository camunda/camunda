/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.search.entities.BatchOperationEntity;

public class BatchOperationEntityMapper {

  public static BatchOperationEntity toEntity(final BatchOperationDbModel dbModel) {
    return new BatchOperationEntity(
        dbModel.batchOperationKey(),
        dbModel.status(),
        dbModel.operationType(),
        dbModel.startDate(),
        dbModel.endDate(),
        dbModel.operationsTotalCount(),
        dbModel.operationsFailedCount(),
        dbModel.operationsCompletedCount());
  }
}
