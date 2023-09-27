/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch.tenant;

import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static io.camunda.operate.schema.indices.IndexDescriptor.TENANT_ID;

import io.camunda.operate.conditions.ElasticsearchCondition;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.tenant.TenantCheckApplier;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.security.tenant.TenantService;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTenantCheckApplier implements TenantCheckApplier<SearchRequest> {

  @Autowired
  private TenantService tenantService;

  @Override
  public SearchRequest apply(final SearchRequest searchRequest) {
    final var tenants = tenantService.getAuthenticatedTenants();

    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var tenantIds = tenants.getTenantIds();
    final var actualQuery = searchRequest.source().query();

    final QueryBuilder finalQuery;

    switch (tenantCheckQueryType) {
      case TENANT_ACCESS_ASSIGNED: {
        final var tenantTermsQuery = termsQuery(TENANT_ID, tenantIds);
        finalQuery = ElasticsearchUtil.joinWithAnd(tenantTermsQuery, actualQuery);
        break;
      }
      case TENANT_ACCESS_NONE: {
        // no data must be returned
        finalQuery = ElasticsearchUtil.createMatchNoneQuery();
        break;
      }
      case TENANT_ACCESS_ALL: {
        finalQuery = actualQuery;
        break;
      }
      default: {
        final var message = String.format("Unexpected tenant check query type %s", tenantCheckQueryType);
        throw new OperateRuntimeException(message);
      }
    }

    // replace query with final query
    searchRequest.source().query(finalQuery);
    return searchRequest;
  }

}
