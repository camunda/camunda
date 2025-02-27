/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.search.entities.BatchOperationEntity;
import java.time.OffsetDateTime;
import java.util.List;

public interface BatchOperationMapper {

  void insert(BatchOperationDbModel batchOperationDbModel);

  void updateCompleted(BatchOperationUpdateDto dto);

  Long count();

  List<BatchOperationEntity> search();

  record BatchOperationUpdateDto(
      long batchOperationKey,
      OffsetDateTime endDate,
      int operationsFailedCount,
      int operationsCompletedCount) {}
}
