/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.tenant;

import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.TENANT_ID;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.tenant.TenantCheckApplier;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.security.tenant.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTenantCheckApplier implements TenantCheckApplier<Query> {

  @Autowired private TenantService tenantService;

  @Override
  public Query apply(final Query query) {
    final var tenantAccess = tenantService.getAuthenticatedTenants();
    final var tenantIds = tenantAccess.tenantIds();

    if (tenantAccess.denied()) {
      return ElasticsearchUtil.createMatchNoneQuery().build()._toQuery();
    } else if (tenantAccess.wildcard()) {
      return query;
    } else {
      final var tenantTermsQuery = ElasticsearchUtil.termsQuery(TENANT_ID, tenantIds);
      return ElasticsearchUtil.joinWithAnd(tenantTermsQuery, query);
    }
  }
}
