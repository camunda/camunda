/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;

public interface BatchOperationSearchClient {

  BatchOperationEntity getBatchOperation(final String id);

  SearchQueryResult<BatchOperationEntity> searchBatchOperations(BatchOperationQuery query);

  SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      BatchOperationItemQuery query);

  BatchOperationSearchClient withSecurityContext(SecurityContext securityContext);
}
