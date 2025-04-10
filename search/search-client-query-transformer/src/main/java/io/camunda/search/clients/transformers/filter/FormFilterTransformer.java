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
import static io.camunda.webapps.schema.descriptors.index.FormIndex.BPMN_ID;
import static io.camunda.webapps.schema.descriptors.index.FormIndex.KEY;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.FormFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class FormFilterTransformer extends IndexFilterTransformer<FormFilter> {

  public FormFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final FormFilter filter) {
    return and(longTerms(KEY, filter.formKeys()), stringTerms(BPMN_ID, filter.formIds()));
  }
}
