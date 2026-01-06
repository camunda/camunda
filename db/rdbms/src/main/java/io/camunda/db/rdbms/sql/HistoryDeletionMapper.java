/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface HistoryDeletionMapper {

  List<HistoryDeletionDbModel> getHistoryDeletionBatch(
      @Param("partitionId") int partitionId, @Param("limit") int limit);

  void insert(HistoryDeletionDbModel historyDeletion);

  int delete(
      @Param("resourceKey") long resourceKey, @Param("batchOperationKey") long batchOperationKey);

  int deleteByResourceKeys(final List<Long> resourceKeys);
}
