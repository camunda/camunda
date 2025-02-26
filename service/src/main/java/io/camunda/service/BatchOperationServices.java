/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.BatchOperationSearchClient;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.query.SearchQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BatchOperationServices {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationServices.class);

  private final BatchOperationSearchClient batchOperationSearchClient;

  public BatchOperationServices(
      final BatchOperationSearchClient batchOperationSearchClient
  ) {
    this.batchOperationSearchClient = batchOperationSearchClient;
  }

  public SearchQueryResult<BatchOperationEntity> search() {
    return batchOperationSearchClient.searchBatchOperations();
  }

}
