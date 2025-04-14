/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.tenant;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantCheckApplier;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.tenant.TenantService;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticSearchCondition.class)
@Component
public class ElasticsearchTenantCheckApplier implements TenantCheckApplier<SearchRequest> {

  @Autowired private TenantService tenantService;

  @Override
  public void apply(final SearchRequest searchRequest) {
    final var tenants = tenantService.getAuthenticatedTenants();
    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var searchByTenantIds = tenants.getTenantIds();

    applyTenantCheckOnQuery(searchRequest, tenantCheckQueryType, searchByTenantIds);
  }

  @Override
  public void apply(final SearchRequest searchRequest, final Collection<String> tenantIds) {
    final var tenants = tenantService.getAuthenticatedTenants();
    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var authorizedTenantIds = Set.copyOf(tenants.getTenantIds());
    final var searchByTenantIds =
        tenantIds.stream().filter(authorizedTenantIds::contains).collect(Collectors.toSet());

    applyTenantCheckOnQuery(searchRequest, tenantCheckQueryType, searchByTenantIds);
  }

  private static void applyTenantCheckOnQuery(
      final SearchRequest searchRequest,
      final TenantService.TenantAccessType tenantCheckQueryType,
      final Collection<String> searchByTenantIds) {
    final var actualQuery = searchRequest.source().query();

    switch (tenantCheckQueryType) {
      case TENANT_ACCESS_ASSIGNED -> {
        final QueryBuilder finalQuery;
        if (CollectionUtils.isEmpty(searchByTenantIds)) {
          // no data must be returned
          finalQuery = ElasticsearchUtil.createMatchNoneQuery();
        } else {
          final var tenantTermsQuery = termsQuery(TENANT_ID, searchByTenantIds);
          finalQuery = ElasticsearchUtil.joinWithAnd(tenantTermsQuery, actualQuery);
        }
        searchRequest.source().query(finalQuery);
      }
      case TENANT_ACCESS_NONE -> // no data must be returned
          searchRequest.source().query(ElasticsearchUtil.createMatchNoneQuery());
      case TENANT_ACCESS_ALL -> searchRequest.source().query(actualQuery);
      default -> {
        final var message =
            String.format("Unexpected tenant check query type %s", tenantCheckQueryType);
        throw new TasklistRuntimeException(message);
      }
    }
  }
}
