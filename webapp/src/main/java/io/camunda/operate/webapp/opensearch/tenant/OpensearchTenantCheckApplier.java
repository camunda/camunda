/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.tenant;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.tenant.TenantCheckApplier;
import io.camunda.operate.webapp.security.tenant.TenantService;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import static io.camunda.operate.schema.indices.IndexDescriptor.TENANT_ID;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchTenantCheckApplier implements TenantCheckApplier<Query>, InitializingBean {

  private static OpensearchTenantCheckApplier instance;

  @Autowired
  private TenantService tenantService;


  @Override
  public void afterPropertiesSet() throws Exception {
    instance = this;
  }

  public static OpensearchTenantCheckApplier get() {
    return instance;
  }

  @Override
  public Query apply(final Query query) {
    final var tenants = tenantService.getAuthenticatedTenants();

    final var tenantCheckQueryType = tenants.getTenantAccessType();
    final var tenantIds = tenants.getTenantIds();

    final Query finalQuery;

    switch (tenantCheckQueryType) {
      case TENANT_ACCESS_ASSIGNED: {
        final var tenantTermsQuery = stringTerms(TENANT_ID, tenantIds);
        finalQuery = and(tenantTermsQuery, query);
        break;
      }
      case TENANT_ACCESS_NONE: {
        // no data must be returned
        finalQuery = matchNone();
        break;
      }
      case TENANT_ACCESS_ALL: {
        finalQuery = query;
        break;
      }
      default: {
        final var message = String.format("Unexpected tenant check query type %s", tenantCheckQueryType);
        throw new OperateRuntimeException(message);
      }
    }

    return finalQuery;
  }

}
