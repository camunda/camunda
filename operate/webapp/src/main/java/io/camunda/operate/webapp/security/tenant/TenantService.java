/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.tenant;

import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class TenantService {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final TenantAccessProvider tenantAccessProvider;
  private final CamundaSecurityLibraryProperties cslProperties;

  public TenantService(
      final CamundaAuthenticationProvider authenticationProvider,
      final TenantAccessProvider tenantAccessProvider,
      final CamundaSecurityLibraryProperties cslProperties) {
    this.authenticationProvider = authenticationProvider;
    this.tenantAccessProvider = tenantAccessProvider;
    this.cslProperties = cslProperties;
  }

  public TenantAccess getAuthenticatedTenants() {
    if (hasNoneRequestContext()) {
      return TenantAccess.wildcard(null);
    }

    if (!isMultiTenancyEnabled()) {
      // the user/app has access to only <default> tenant
      return TenantAccess.allowed(List.of(DEFAULT_TENANT_ID));
    }

    final var currentAuthentication = authenticationProvider.getCamundaAuthentication();
    return tenantAccessProvider.resolveTenantAccess(currentAuthentication);
  }

  private boolean isMultiTenancyEnabled() {
    return cslProperties.getMultiTenancy().isChecksEnabled();
  }

  private boolean hasNoneRequestContext() {
    return RequestContextHolder.getRequestAttributes() == null;
  }
}
