/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class BatchOperationDocumentReader extends DocumentBasedReader
    implements BatchOperationReader {

  public BatchOperationDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public BatchOperationEntity getById(
      final String id, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            BatchOperationQuery.of(b -> b.filter(f -> f.batchOperationKeys(id)).singleResult()),
            io.camunda.webapps.schema.entities.operation.BatchOperationEntity.class);
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> search(
      final BatchOperationQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.operation.BatchOperationEntity.class,
            resourceAccessChecks);
  }
}
