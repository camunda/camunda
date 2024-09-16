/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.sort;

import io.camunda.search.transformers.ServiceTransformers;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.query.TypedSearchQuery;
import io.camunda.service.search.sort.SearchSortOptions;
import io.camunda.service.search.sort.SortOption;
import java.util.List;

public class AbstractSortTransformerTest {

  private final ServiceTransformers transformers = ServiceTransformers.newInstance();

  protected List<SearchSortOptions> transformRequest(
      TypedSearchQuery<? extends FilterBase, ? extends SortOption> request
  ) {
    return transformers.getTypedSearchQueryTransformer(request.getClass()).apply((TypedSearchQuery<FilterBase, SortOption>) request).sort();
  }
}
