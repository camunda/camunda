/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.tenant.TenantCheckApplier;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTenantHelper {
  private final Optional<TenantCheckApplier<Query>> es8TenantCheckApplier;

  // Using optional as this is equivalent to @Autowired(required=false) for constructor injection
  public ElasticsearchTenantHelper(
      final Optional<TenantCheckApplier<Query>> es8TenantCheckApplier) {
    this.es8TenantCheckApplier = es8TenantCheckApplier;
  }

  public Query makeQueryTenantAware(final Query query) {
    if (es8TenantCheckApplier.isEmpty()) {
      return query;
    }

    return es8TenantCheckApplier.get().apply(query);
  }
}
