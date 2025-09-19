/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class CorrelatedMessageSubscriptionDocumentReader extends DocumentBasedReader
    implements CorrelatedMessageSubscriptionReader {

  public CorrelatedMessageSubscriptionDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<CorrelatedMessageSubscriptionEntity> search(
      final CorrelatedMessageSubscriptionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity.class,
            resourceAccessChecks);
  }
}
