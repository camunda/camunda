/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.tenant;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.security.reader.TenantAccess;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantCheckApplier;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.tenant.TenantService;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticSearchCondition.class)
@Component
public class ElasticsearchEs8TenantCheckApplier implements TenantCheckApplier<Query> {

  @Autowired private TenantService tenantService;

  @Override
  public Query apply(final Query query) {
    final var tenantAccess = tenantService.getAuthenticatedTenants();
    return applyTenantCheckOnQuery(query, tenantAccess, tenantAccess.tenantIds());
  }

  @Override
  public Query apply(final Query query, final Collection<String> tenantIds) {
    final var tenantAccess = tenantService.getAuthenticatedTenants();
    final var authorizedTenantIds =
        Optional.ofNullable(tenantAccess.tenantIds()).map(Set::copyOf).orElseGet(HashSet::new);
    final var searchByTenantIds =
        tenantIds.stream().filter(authorizedTenantIds::contains).collect(Collectors.toSet());

    return applyTenantCheckOnQuery(query, tenantAccess, searchByTenantIds);
  }

  private static Query applyTenantCheckOnQuery(
      final Query query,
      final TenantAccess tenantAccess,
      final Collection<String> searchByTenantIds) {

    if (tenantAccess.wildcard()) {
      return query;

    } else if (tenantAccess.denied() || CollectionUtils.isEmpty(searchByTenantIds)) {
      return ElasticsearchUtil.createMatchNoneQueryEs8().build()._toQuery();

    } else if (tenantAccess.allowed()) {
      final var tenantTermsQuery = ElasticsearchUtil.termsQuery(TENANT_ID, searchByTenantIds);
      return ElasticsearchUtil.joinWithAnd(tenantTermsQuery, query);

    } else {
      final var message = String.format("Unexpected tenant access type %s", tenantAccess);
      throw new TasklistRuntimeException(message);
    }
  }
}
