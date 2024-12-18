/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.List;
import java.util.Map;

public interface BatchOperationUpdateRepository extends AutoCloseable {

  /**
   * Returns the list of not finished batch operations. We can use endDate field to distinguish
   * finished from running.
   */
  List<BatchOperationEntity> getNotFinishedBatchOperations();

  /**
   * Counts amount of single operations that are finished (COMPLETED or FAILED state) that are
   * included in given batch operations. Those batch operations that does not have any finished
   * operations will not be included in the result. Therefore, the result list size may be less than
   * the input list size.
   *
   * @param batchOperationIds list of batch operation ids
   */
  List<OperationsAggData> getFinishedOperationsCount(List<String> batchOperationIds);

  /**
   * Updates the batch operations with the amount of finished operations. Update method additionally
   * includes the script to set endDate field to the current time for those batch operations that
   * have all operations finished (operationsTotalCount <= operationsFinishedCount).
   *
   * @param documentUpdates
   * @return
   */
  Integer bulkUpdate(List<DocumentUpdate> documentUpdates);

  /**
   * Represents a specific document store agnostic update to execute.
   *
   * <p>All fields are expected to be non-null, except routing.
   */
  record DocumentUpdate(String id, Map<String, Object> doc) {}

  record OperationsAggData(String batchOperationId, long finishedOperationsCount) {}
}
