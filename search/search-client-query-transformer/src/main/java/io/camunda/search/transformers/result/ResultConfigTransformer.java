/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.result;

import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.service.search.result.QueryResultConfig;
import io.camunda.service.search.result.QueryResultConfig.FieldFilter;
import io.camunda.search.transformers.ServiceTransformer;
import java.util.List;
import java.util.stream.Collectors;

public final class ResultConfigTransformer
    implements ServiceTransformer<QueryResultConfig, SearchSourceConfig> {

  @Override
  public SearchSourceConfig apply(final QueryResultConfig value) {
    if (value != null) {
      final var builder = new SearchSourceConfig.Builder();
      final List<FieldFilter> fieldFilters = value.getFieldFilters();
      if (fieldFilters != null && !fieldFilters.isEmpty()) {
        builder.filter(toSearchSourceFilter(fieldFilters));
      }
      return builder.build();
    }
    return null;
  }

  private SearchSourceFilter toSearchSourceFilter(final List<FieldFilter> value) {
    final var includedVsExcludedMap =
        value.stream()
            .collect(
                Collectors.partitioningBy(
                    FieldFilter::include,
                    Collectors.mapping(FieldFilter::field, Collectors.toList())));
    return new SearchSourceFilter.Builder()
        .includes(includedVsExcludedMap.get(true))
        .excludes(includedVsExcludedMap.get(false))
        .build();
  }
}
