/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.bool;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.FORM_ID;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.FORM_KEY;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.RESOURCE_NAME;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.VERSION;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.VERSION_TAG;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class ProcessDefinitionFilterTransformer
    extends IndexFilterTransformer<ProcessDefinitionFilter> {

  public ProcessDefinitionFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessDefinitionFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(longTerms(KEY, filter.processDefinitionKeys())).ifPresent(queries::add);
    ofNullable(getNamesQuery(filter.nameOperations())).ifPresent(queries::addAll);
    ofNullable(getProcessDefinitionIdsQuery(filter.processDefinitionIdOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringTerms(RESOURCE_NAME, filter.resourceNames())).ifPresent(queries::add);
    ofNullable(intTerms(VERSION, filter.versions())).ifPresent(queries::add);
    ofNullable(stringTerms(VERSION_TAG, filter.versionTags())).ifPresent(queries::add);
    ofNullable(stringTerms(TENANT_ID, filter.tenantIds())).ifPresent(queries::add);
    ofNullable(getFormKeyQuery(filter.hasFormKey())).ifPresent(queries::add);
    return and(queries);
  }

  private List<SearchQuery> getNamesQuery(final List<Operation<String>> names) {
    return stringOperations(NAME, names);
  }

  private List<SearchQuery> getProcessDefinitionIdsQuery(
      final List<Operation<String>> processDefinitionIds) {
    return stringOperations(BPMN_PROCESS_ID, processDefinitionIds);
  }

  private SearchQuery getFormKeyQuery(final Boolean hasFormKey) {
    if (hasFormKey != null) {
      return bool(b -> {
        if (hasFormKey) {
          b.must(List.of(SearchQueryBuilders.exists(FORM_ID)));
        } else {
          b.mustNot(List.of(SearchQueryBuilders.exists(FORM_ID)));
        }
        return b;
      }).toSearchQuery();
    }
    return null;
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
