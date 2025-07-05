/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.*;
import static io.camunda.search.exception.ErrorMessages.ERROR_UNSUPPORTED_BATCH_OPERATION_AUTHORIZATION_FILTER;
import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.*;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public final class BatchOperationFilterTransformer
    extends IndexFilterTransformer<BatchOperationFilter> {

  public BatchOperationFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public BatchOperationFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessFilter resourceAccessManager) {
    super(indexDescriptor, resourceAccessManager);
  }

  @Override
  public BatchOperationFilterTransformer withResourceAccessFilter(
      final ResourceAccessFilter resourceAccessFilter) {
    return new BatchOperationFilterTransformer(indexDescriptor, resourceAccessFilter);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    throw new CamundaSearchException(ERROR_UNSUPPORTED_BATCH_OPERATION_AUTHORIZATION_FILTER);
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    // no tenant concept to apply yet
    return matchAll();
  }

  @Override
  public SearchQuery toSearchQuery(final BatchOperationFilter filter) {
    return and(
        stringOperations(ID, filter.batchOperationIdOperations()),
        stringOperations(STATE, filter.stateOperations()),
        stringOperations(TYPE, filter.operationTypeOperations()));
  }
}
