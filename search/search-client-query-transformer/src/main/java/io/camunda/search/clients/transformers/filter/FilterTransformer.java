/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import io.camunda.search.clients.control.ResourceAccessControl;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.filter.FilterBase;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public interface FilterTransformer<T extends FilterBase>
    extends ServiceTransformer<T, SearchQuery> {

  @Override
  default SearchQuery apply(final T filter) {
    return toSearchQuery(filter);
  }

  SearchQuery toSearchQuery(final T filter);

  default IndexDescriptor getIndex() {
    throw new IllegalArgumentException("Filter does not support indices");
  }

  default FilterTransformer<T> withResourceAccessControl(
      final ResourceAccessControl resourceAccessControl) {
    return this;
  }
}
