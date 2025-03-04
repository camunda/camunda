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
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;
import java.util.List;

public interface BatchOperationSearchClient {

  SearchQueryResult<BatchOperationEntity> searchBatchOperations(BatchOperationQuery query);

  List<BatchOperationItemEntity> getBatchOperationItems(Long batchOperationKey);

}
