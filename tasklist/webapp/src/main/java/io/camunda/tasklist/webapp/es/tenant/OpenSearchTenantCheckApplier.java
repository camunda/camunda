/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.tenant;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantCheckApplier;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.webapp.tenant.TenantService;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpenSearchCondition.class)
@Component
public class OpenSearchTenantCheckApplier implements TenantCheckApplier<SearchRequest.Builder> {

  @Autowired private TenantService tenantService;

  @Override
  public void apply(final SearchRequest.Builder searchRequest) {
    final var tenants = tenantService.getAuthenticatedTenants();
    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var searchByTenantIds = tenants.getTenantIds();

    applyTenantCheckOnQuery(searchRequest, tenantCheckQueryType, searchByTenantIds);
  }

  @Override
  public void apply(final SearchRequest.Builder searchRequest, final Collection<String> tenantIds) {
    final var tenants = tenantService.getAuthenticatedTenants();
    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var authorizedTenantIds = Set.copyOf(tenants.getTenantIds());
    final var searchByTenantIds =
        tenantIds.stream().filter(authorizedTenantIds::contains).collect(Collectors.toSet());

    applyTenantCheckOnQuery(searchRequest, tenantCheckQueryType, searchByTenantIds);
  }

  private void applyTenantCheckOnQuery(
      final SearchRequest.Builder searchRequest,
      final TenantService.TenantAccessType tenantCheckQueryType,
      final Collection<String> searchByTenantIds) {
    final var actualQuery = getQueryFromSearchRequestBuilder(searchRequest);

    switch (tenantCheckQueryType) {
      case TENANT_ACCESS_ASSIGNED -> {
        final Query finalQuery;
        if (CollectionUtils.isEmpty(searchByTenantIds)) {
          // no data must be returned
          finalQuery = OpenSearchUtil.createMatchNoneQuery();
        } else {
          final var tenantTermsQuery =
              new Query.Builder()
                  .terms(
                      terms ->
                          terms
                              .field(TENANT_ID)
                              .terms(
                                  values ->
                                      values.value(
                                          searchByTenantIds.stream()
                                              .map(FieldValue::of)
                                              .collect(Collectors.toList()))));
          finalQuery = OpenSearchUtil.joinWithAnd(tenantTermsQuery.build(), actualQuery);
        }
        searchRequest.query(finalQuery);
      }
      case TENANT_ACCESS_NONE -> // no data must be returned
          searchRequest.query(OpenSearchUtil.createMatchNoneQuery());
      case TENANT_ACCESS_ALL -> // return without changing anything in the query
          searchRequest.query(actualQuery);
      default -> {
        final var message =
            String.format("Unexpected tenant check query type %s", tenantCheckQueryType);
        throw new TasklistRuntimeException(message);
      }
    }
  }

  private Query getQueryFromSearchRequestBuilder(final SearchRequest.Builder searchRequest) {
    try {
      final Field privateField = SearchRequest.Builder.class.getDeclaredField("query");
      privateField.setAccessible(true);

      // Store the value of private field in variable
      return (Query) privateField.get(searchRequest);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
