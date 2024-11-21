/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex.BPMN_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex.KEY;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FormFilter;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import java.util.Arrays;
import java.util.List;

public class FormFilterTransformer implements FilterTransformer<FormFilter> {

  private final ServiceTransformers serviceTransformers;
  private final String prefix;

  public FormFilterTransformer(final ServiceTransformers transformers, final String prefix) {
    serviceTransformers = transformers;
    this.prefix = prefix;
  }

  @Override
  public SearchQuery toSearchQuery(final FormFilter filter) {
    final var formKeyQuery = getFormByKeysQuery(filter.formKey());
    final var formIdQuery = getFormByIdQuery(filter.formId());

    return and(formKeyQuery, formIdQuery);
  }

  @Override
  public List<String> toIndices(final FormFilter filter) {
    final String indexName = FormIndex.getIndexNameWithPrefix(prefix);
    System.out.println(indexName);
    return Arrays.asList(indexName);
  }

  private SearchQuery getFormByKeysQuery(final List<Long> formKeys) {
    return longTerms(KEY, formKeys);
  }

  private SearchQuery getFormByIdQuery(final List<String> formIds) {
    return stringTerms(BPMN_ID, formIds);
  }
}
