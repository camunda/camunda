/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.core.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.WaitingStateEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.WaitingStateQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.security.api.ResourceAccessChecks;

public class WaitingStateDocumentReader extends DocumentBasedReader implements WaitingStateReader {

  public WaitingStateDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<WaitingStateEntity> search(
      final WaitingStateQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.WaitingStateEntity.class,
            resourceAccessChecks);
  }
}
