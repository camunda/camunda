/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.tenant;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;

import io.camunda.security.reader.TenantAccess;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantCheckApplier;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.webapp.tenant.TenantService;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
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
  public SearchRequest.Builder apply(final SearchRequest.Builder searchRequest) {
    final var tenantAccess = tenantService.getAuthenticatedTenants();
    applyTenantCheckOnQuery(searchRequest, tenantAccess, tenantAccess.tenantIds());
    return searchRequest;
  }

  @Override
  public SearchRequest.Builder apply(
      final SearchRequest.Builder searchRequest, final Collection<String> tenantIds) {
    final var tenantAccess = tenantService.getAuthenticatedTenants();
    final var authorizedTenantIds =
        Optional.ofNullable(tenantAccess.tenantIds()).map(Set::copyOf).orElseGet(HashSet::new);
    final var searchByTenantIds =
        tenantIds.stream().filter(authorizedTenantIds::contains).collect(Collectors.toSet());

    applyTenantCheckOnQuery(searchRequest, tenantAccess, searchByTenantIds);

    return searchRequest;
  }

  private void applyTenantCheckOnQuery(
      final SearchRequest.Builder searchRequest,
      final TenantAccess tenantAccess,
      final Collection<String> searchByTenantIds) {
    final var actualQuery = getQueryFromSearchRequestBuilder(searchRequest);

    if (tenantAccess.wildcard()) {
      searchRequest.query(actualQuery);

    } else if (tenantAccess.denied() || CollectionUtils.isEmpty(searchByTenantIds)) {
      searchRequest.query(OpenSearchUtil.createMatchNoneQuery());

    } else if (tenantAccess.allowed()) {
      final var tenantTermsQuery = getTenantTermsQuery(searchByTenantIds);
      final var finalQuery = OpenSearchUtil.joinWithAnd(tenantTermsQuery, actualQuery);
      searchRequest.query(finalQuery);

    } else {
      final var message = String.format("Unexpected tenant access type %s", tenantAccess);
      throw new TasklistRuntimeException(message);
    }
  }

  private Query getTenantTermsQuery(final Collection<String> searchByTenantIds) {
    return new Query.Builder()
        .terms(
            terms ->
                terms
                    .field(TENANT_ID)
                    .terms(
                        values ->
                            values.value(
                                searchByTenantIds.stream()
                                    .map(FieldValue::of)
                                    .collect(Collectors.toList()))))
        .build();
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
