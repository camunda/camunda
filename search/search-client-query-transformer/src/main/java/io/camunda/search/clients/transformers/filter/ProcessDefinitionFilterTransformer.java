/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.RESOURCE_NAME;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.VERSION;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.VERSION_TAG;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class ProcessDefinitionFilterTransformer
    extends IndexFilterTransformer<ProcessDefinitionFilter> {

  public ProcessDefinitionFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public ProcessDefinitionFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessResult resourceAccessManager) {
    super(indexDescriptor, resourceAccessManager);
  }

  @Override
  public ProcessDefinitionFilterTransformer withResourceAccessFilter(
      final ResourceAccessResult resourceAccessResult) {
    return new ProcessDefinitionFilterTransformer(indexDescriptor, resourceAccessResult);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    final var resourceIds = authorization.resourceIds();
    return stringTerms(BPMN_PROCESS_ID, resourceIds);
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    return stringTerms(TENANT_ID, tenantIds);
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessDefinitionFilter filter) {
    return and(
        longTerms(KEY, filter.processDefinitionKeys()),
        stringTerms(NAME, filter.names()),
        stringTerms(BPMN_PROCESS_ID, filter.processDefinitionIds()),
        stringTerms(RESOURCE_NAME, filter.resourceNames()),
        intTerms(VERSION, filter.versions()),
        stringTerms(VERSION_TAG, filter.versionTags()),
        stringTerms(TENANT_ID, filter.tenantIds()));
  }
}
