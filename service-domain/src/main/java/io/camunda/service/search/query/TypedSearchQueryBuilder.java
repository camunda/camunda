/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.sort.SortOption;
import io.camunda.util.ObjectBuilder;

public interface TypedSearchQueryBuilder<
        T, B extends ObjectBuilder<T>, F extends FilterBase, S extends SortOption>
    extends ObjectBuilder<T> {

  B filter(F value);

  B sort(S value);

  B page(SearchQueryPage value);
}
