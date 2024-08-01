/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.source;

import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.service.search.source.SourceConfig.FieldFilter;
import io.camunda.service.transformers.ServiceTransformer;
import java.util.List;
import java.util.stream.Collectors;

public final class SourceFilterTransformer
    implements ServiceTransformer<List<FieldFilter>, SearchSourceFilter> {

  @Override
  public SearchSourceFilter apply(final List<FieldFilter> value) {
    final var map =
        value.stream()
            .collect(
                Collectors.partitioningBy(
                    FieldFilter::include,
                    Collectors.mapping(FieldFilter::field, Collectors.toList())));
    return new SearchSourceFilter.Builder()
        .includes(map.get(true))
        .excludes(map.get(false))
        .build();
  }
}
