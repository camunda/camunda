/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.tenant.TenantCheckApplier;
import java.util.Collection;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Helper class for applying tenant checks to ES8 queries. This is used to ensure multi-tenancy
 * security by filtering queries based on the authenticated user's tenant access.
 */
@Conditional(ElasticSearchCondition.class)
@Component
public class ElasticsearchTenantHelper {
  private final Optional<TenantCheckApplier<Query>> es8TenantCheckApplier;

  // Using Optional as this is equivalent to @Autowired(required=false) for constructor injection
  public ElasticsearchTenantHelper(
      final Optional<TenantCheckApplier<Query>> es8TenantCheckApplier) {
    this.es8TenantCheckApplier = es8TenantCheckApplier;
  }

  /**
   * Makes a query tenant-aware by applying tenant filtering based on the authenticated user's
   * tenant access.
   *
   * @param query The original query
   * @return A new query with tenant filtering applied, or the original query if tenant checking is
   *     disabled
   */
  public Query makeQueryTenantAware(final Query query) {
    if (es8TenantCheckApplier.isEmpty()) {
      return query;
    }

    return es8TenantCheckApplier.get().apply(query);
  }

  public Query makeQueryTenantAware(final Query query, final Collection<String> tenantIds) {
    if (es8TenantCheckApplier.isEmpty()) {
      return query;
    }

    return es8TenantCheckApplier.get().apply(query, tenantIds);
  }
}
