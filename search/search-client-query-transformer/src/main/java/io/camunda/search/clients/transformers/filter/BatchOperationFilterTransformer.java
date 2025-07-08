/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.*;
import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.*;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public final class BatchOperationFilterTransformer
    extends IndexFilterTransformer<BatchOperationFilter> {

  public BatchOperationFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final BatchOperationFilter filter) {
    return and(
        stringOperations(ID, filter.batchOperationKeyOperations()),
        stringOperations(STATE, filter.stateOperations()),
        stringOperations(TYPE, filter.operationTypeOperations()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(ID, authorization.resourceIds());
  }

  @Override
  protected SearchQuery toTenantCheckSearchQuery(final List<String> tenantIds) {
    return SearchQueryBuilders.matchAll();
  }
}
