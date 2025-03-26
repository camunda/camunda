/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.impl.search.request;

import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import java.util.List;

public class SearchRequestPageImpl
    extends TypedSearchRequestPropertyProvider<SearchQueryPageRequest>
    implements SearchRequestPage {

  private final SearchQueryPageRequest page;

  public SearchRequestPageImpl() {
    page = new SearchQueryPageRequest();
  }

  @Override
  public SearchRequestPage from(final Integer value) {
    page.setFrom(value);
    return this;
  }

  @Override
  public SearchRequestPage limit(final Integer value) {
    page.setLimit(value);
    return this;
  }

  @Override
  public SearchRequestPage searchBefore(final List<Object> values) {
    page.setSearchBefore(values);
    return this;
  }

  @Override
  public SearchRequestPage searchAfter(final List<Object> values) {
    page.setSearchAfter(values);
    return this;
  }

  @Override
  public SearchQueryPageRequest getSearchRequestProperty() {
    return page;
  }
}
