/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.VariableHistoryItemEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableHistoryItemQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class VariableHistoryDocumentReader extends DocumentBasedReader
    implements VariableHistoryReader {

  public VariableHistoryDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<VariableHistoryItemEntity> search(
      final VariableHistoryItemQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.variablehistory.VariableHistoryEntity.class,
            resourceAccessChecks);
  }

  @Override
  public VariableHistoryItemEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "VariableHistoryDocumentReader#getByKey() not implemented yet, see"
            + " camunda/pdp-3517-variable-trace#183");
  }
}
