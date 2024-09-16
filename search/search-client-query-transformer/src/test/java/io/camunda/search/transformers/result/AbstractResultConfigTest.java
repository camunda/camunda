/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.result;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.transformers.ServiceTransformers;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.query.TypedSearchQuery;
import io.camunda.service.search.sort.SearchSortOptions;
import io.camunda.service.search.sort.SortOption;
import java.util.List;

public class AbstractResultConfigTest {

  private final ServiceTransformers transformers = ServiceTransformers.newInstance();

  protected SearchSourceConfig transformRequest(
      TypedSearchQuery<? extends FilterBase, ? extends SortOption> request
  ) {
    return transformers.getTypedSearchQueryTransformer(request.getClass()).apply((TypedSearchQuery<FilterBase, SortOption>) request).source();
  }
}
