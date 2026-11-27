/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.CaseFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.CaseIndex;
import java.util.ArrayList;

public final class CaseFilterTransformer extends IndexFilterTransformer<CaseFilter> {

  public CaseFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final CaseFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(longOperations(CaseIndex.KEY, filter.caseKeyOperations()));
    queries.addAll(
        longOperations(CaseIndex.PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()));
    queries.addAll(
        stringOperations(CaseIndex.BPMN_PROCESS_ID, filter.processDefinitionIdOperations()));
    queries.addAll(stringOperations(CaseIndex.BUSINESS_ID, filter.businessIdOperations()));
    queries.addAll(stringOperations(CaseIndex.TITLE, filter.titleOperations()));
    queries.addAll(stringOperations(CaseIndex.STATE, filter.stateOperations()));
    queries.addAll(stringOperations(CaseIndex.TENANT_ID, filter.tenantIdOperations()));
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    return stringTerms(CaseIndex.BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
