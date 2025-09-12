/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.dateTimeOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UsageMetricsTUFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import java.util.ArrayList;
import java.util.List;

public class UsageMetricsTUFilterTransformer extends IndexFilterTransformer<UsageMetricsTUFilter> {

  public UsageMetricsTUFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final UsageMetricsTUFilter filter) {
    final var queries =
        new ArrayList<>(
            dateTimeOperations(
                UsageMetricTUIndex.END_TIME,
                List.of(Operation.gte(filter.startTime()), Operation.lt(filter.endTime()))));

    if (filter.tenantId() != null) {
      queries.add(term(UsageMetricTUIndex.TENANT_ID, filter.tenantId()));
    }

    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return matchAll();
  }
}
