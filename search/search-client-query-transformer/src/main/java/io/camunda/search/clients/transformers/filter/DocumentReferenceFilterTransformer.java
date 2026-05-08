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
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.DOCUMENT_ID;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.VARIABLE_KEY;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.DocumentReferenceFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;

public class DocumentReferenceFilterTransformer
    extends IndexFilterTransformer<DocumentReferenceFilter> {

  public DocumentReferenceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final DocumentReferenceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()));
    queries.addAll(longOperations(SCOPE_KEY, filter.scopeKeyOperations()));
    queries.addAll(longOperations(VARIABLE_KEY, filter.variableKeyOperations()));
    queries.addAll(stringOperations(DOCUMENT_ID, filter.documentIdOperations()));
    ofNullable(stringTerms(TENANT_ID, filter.tenantIds())).ifPresent(queries::add);
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
