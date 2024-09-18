/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.FormFilter;
import io.camunda.service.transformers.ServiceTransformers;
import java.util.Arrays;
import java.util.List;

public class FormFilterTransformer implements FilterTransformer<FormFilter> {

  private final ServiceTransformers transformers;

  public FormFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final FormFilter filter) {
    final var formKeyQuery = getFormByKeysQuery(filter.keys());

    return and(formKeyQuery);
  }

  @Override
  public List<String> toIndices(final FormFilter filter) {
    return Arrays.asList("tasklist-form-8.4.0_");
  }

  private SearchQuery getFormByKeysQuery(final List<Long> formKey) {
    final List<String> formKeyAsString = formKey.stream()
        .map(String::valueOf)
        .toList();

    return stringTerms("id", formKeyAsString);
  }
}
