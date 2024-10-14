/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.tenant;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.TENANT_ID;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.tenant.TenantCheckApplier;
import io.camunda.operate.webapp.security.tenant.TenantService;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchTenantCheckApplier implements TenantCheckApplier<Query>, InitializingBean {

  private static OpensearchTenantCheckApplier instance;

  @Autowired private TenantService tenantService;

  public static OpensearchTenantCheckApplier get() {
    return instance;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    instance = this;
  }

  @Override
  public Query apply(final Query query) {
    final var tenants = tenantService.getAuthenticatedTenants();

    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var tenantIds = tenants.getTenantIds();

    final Query finalQuery;

    switch (tenantCheckQueryType) {
      case TENANT_ACCESS_ASSIGNED:
        {
          final var tenantTermsQuery = stringTerms(TENANT_ID, tenantIds);
          finalQuery = and(tenantTermsQuery, query);
          break;
        }
      case TENANT_ACCESS_NONE:
        {
          // no data must be returned
          finalQuery = matchNone();
          break;
        }
      case TENANT_ACCESS_ALL:
        {
          finalQuery = query;
          break;
        }
      default:
        {
          final var message =
              String.format("Unexpected tenant check query type %s", tenantCheckQueryType);
          throw new OperateRuntimeException(message);
        }
    }

    return finalQuery;
  }
}
