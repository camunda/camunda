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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.CREATION_TIME;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.ERROR_CODE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.ERROR_MESSAGE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.JobErrorStatisticsFilter;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class JobErrorStatisticsFilterTransformer
    extends IndexFilterTransformer<JobErrorStatisticsFilter> {

  public JobErrorStatisticsFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final JobErrorStatisticsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();

    if (filter.from() != null) {
      queries.addAll(dateTimeOperations(CREATION_TIME, List.of(Operation.gte(filter.from()))));
    }

    if (filter.to() != null) {
      queries.addAll(dateTimeOperations(CREATION_TIME, List.of(Operation.lte(filter.to()))));
    }

    if (filter.jobType() != null) {
      queries.add(term(JOB_TYPE, filter.jobType()));
    }

    if (!filter.errorCodeOperations().isEmpty()) {
      queries.addAll(stringOperations(ERROR_CODE, filter.errorCodeOperations()));
    }

    if (!filter.errorMessageOperations().isEmpty()) {
      queries.addAll(stringOperations(ERROR_MESSAGE, filter.errorMessageOperations()));
    }

    return queries.isEmpty() ? matchAll() : and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return matchAll();
  }
}
