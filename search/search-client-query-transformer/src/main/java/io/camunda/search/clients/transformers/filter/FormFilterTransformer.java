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
import static io.camunda.webapps.schema.descriptors.index.FormIndex.TENANT_ID;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FormFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class FormFilterTransformer extends IndexFilterTransformer<FormFilter> {

  public FormFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public FormFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessFilter resourceAccessManager) {
    super(indexDescriptor, resourceAccessManager);
  }

  @Override
  public FormFilterTransformer withResourceAccessFilter(
      final ResourceAccessFilter resourceAccessFilter) {
    return new FormFilterTransformer(indexDescriptor, resourceAccessFilter);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    throw new CamundaSearchException("No authorization checks can be applied");
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    return stringTerms(TENANT_ID, tenantIds);
  }

  @Override
  public SearchQuery toSearchQuery(final FormFilter filter) {
    return and(longTerms(KEY, filter.formKeys()), stringTerms(BPMN_ID, filter.formIds()));
  }
}
