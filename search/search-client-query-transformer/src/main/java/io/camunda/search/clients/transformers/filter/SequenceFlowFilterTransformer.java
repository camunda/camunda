/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.SequenceFlowFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import java.util.List;

public final class SequenceFlowFilterTransformer
    extends IndexFilterTransformer<SequenceFlowFilter> {

  public SequenceFlowFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public SequenceFlowFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessResult resourceAccessManager) {
    super(indexDescriptor, resourceAccessManager);
  }

  @Override
  public SequenceFlowFilterTransformer withResourceAccessFilter(
      final ResourceAccessResult resourceAccessResult) {
    return new SequenceFlowFilterTransformer(indexDescriptor, resourceAccessResult);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    final var resourceIds = authorization.resourceIds();
    return stringTerms(SequenceFlowTemplate.BPMN_PROCESS_ID, resourceIds);
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    return stringTerms(SequenceFlowTemplate.TENANT_ID, tenantIds);
  }

  @Override
  public SearchQuery toSearchQuery(final SequenceFlowFilter filter) {
    return term(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, filter.processInstanceKey());
  }
}
