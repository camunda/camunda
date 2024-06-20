/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.tenant;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantServiceImpl implements TenantService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TenantServiceImpl.class);

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public AuthenticatedTenants getAuthenticatedTenants() {
    if (!isMultiTenancyEnabled()) {
      // disabled means no tenant check necessary.
      // thus, the user/app has access to all tenants.
      return AuthenticatedTenants.allTenants();
    }

    final var authentication = getCurrentTenantAwareAuthentication();
    final var tenants = getTenantsFromAuthentication(authentication);

    if (CollectionUtils.isNotEmpty(tenants)) {
      return AuthenticatedTenants.assignedTenants(tenants);
    } else {
      return AuthenticatedTenants.noTenantsAssigned();
    }
  }

  @Override
  public boolean isTenantValid(final String tenantId) {
    if (isMultiTenancyEnabled()) {
      return getAuthenticatedTenants().contains(tenantId);
    } else {
      return true;
    }
  }

  @Override
  public boolean isMultiTenancyEnabled() {
    return tasklistProperties.getMultiTenancy().isEnabled()
        && SecurityContextHolder.getContext().getAuthentication() != null;
  }

  private TenantAwareAuthentication getCurrentTenantAwareAuthentication() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final TenantAwareAuthentication currentAuthentication;

    if (authentication instanceof final TenantAwareAuthentication tenantAwareAuthentication) {
      currentAuthentication = tenantAwareAuthentication;
    } else {
      currentAuthentication = null;
      // log error message for visibility
      final var message =
          String.format(
              "Multi Tenancy is not supported with current authentication type %s",
              authentication.getClass());
      LOGGER.error(message, new TasklistRuntimeException());
    }

    return currentAuthentication;
  }

  private List<String> getTenantsFromAuthentication(
      final TenantAwareAuthentication authentication) {
    final var authenticatedTenants = new ArrayList<String>();

    if (authentication != null) {
      final var tenants = authentication.getTenants();
      if (tenants != null && !tenants.isEmpty()) {
        tenants.stream()
            .map(TasklistTenant::getId)
            .collect(Collectors.toCollection(() -> authenticatedTenants));
      }
    }

    return authenticatedTenants;
  }
}
