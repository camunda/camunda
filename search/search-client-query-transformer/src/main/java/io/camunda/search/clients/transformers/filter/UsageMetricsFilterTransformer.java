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

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import java.util.List;

public class UsageMetricsFilterTransformer extends IndexFilterTransformer<UsageMetricsFilter> {

  public UsageMetricsFilterTransformer(final MetricIndex metricIndex) {
    super(metricIndex);
  }

  @Override
  public SearchQuery toSearchQuery(final UsageMetricsFilter filter) {
    return and(
        dateTimeOperations(
            "eventTime",
            List.of(Operation.gte(filter.startTime()), Operation.lte(filter.endTime()))));
  }
}
