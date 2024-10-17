/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.util.tenant;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT;
import static java.util.stream.Collectors.toMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CCSMCondition.class)
@Component
public class CamundaCCSMTenantAuthorizationService implements DataSourceTenantAuthorizationService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CamundaCCSMTenantAuthorizationService.class);
  private final CCSMTokenService ccsmTokenService;
  private final ConfigurationService configurationService;
  private final Cache<String, List<TenantDto>> userTenantAuthorizations;

  public CamundaCCSMTenantAuthorizationService(
      final CCSMTokenService ccsmTokenService, final ConfigurationService configurationService) {
    this.ccsmTokenService = ccsmTokenService;
    this.configurationService = configurationService;
    userTenantAuthorizations =
        Caffeine.newBuilder()
            .maximumSize(
                configurationService.getCaches().getCloudTenantAuthorizations().getMaxSize())
            .expireAfterWrite(
                configurationService
                    .getCaches()
                    .getCloudTenantAuthorizations()
                    .getDefaultTtlMillis(),
                TimeUnit.MILLISECONDS)
            .build();
  }

  @Override
  public boolean isAuthorizedToSeeAllTenants(
      final String identityId, final IdentityType identityType, final List<String> tenantIds) {
    // In CCSM, we can only retrieve tenant auths for the current user using the user's token, so we
    // have no use for the other
    // params
    return isCurrentUserAuthorizedToSeeAllTenants(tenantIds);
  }

  @Override
  public boolean isAuthorizedToSeeTenant(
      final String identityId, final IdentityType identityType, final String tenantId) {
    // In CCSM, we can only retrieve tenant auths for the current user using the user's token, so we
    // have no use for the other
    // params
    return isCurrentUserAuthorizedToSeeTenant(tenantId);
  }

  @Override
  public boolean isAuthorizedToSeeTenant(
      final String identityId,
      final IdentityType identityType,
      final String tenantId,
      final String dataSourceName) {
    // In CCSM, we can only retrieve tenant auths for the current user using the user's token, so we
    // have no use for the other
    // params. Datasource is always zeebe in CCSM
    return isCurrentUserAuthorizedToSeeTenant(tenantId);
  }

  public Map<String, TenantDto> getCurrentUserTenantAuthorizations() {
    return getCurrentUserAuthorizedTenants().stream()
        .collect(toMap(TenantDto::getId, Function.identity()));
  }

  public List<TenantDto> getCurrentUserAuthorizedTenants() {
    if (configurationService.isMultiTenancyEnabled()) {
      final Optional<String> currentUserId = getCurrentUserId();
      if (currentUserId.isEmpty()) {
        log.warn(
            "Unable to determine currently logged in user ID to retrieve tenant authorizations.");
        return Collections.emptyList();
      }
      Optional<List<TenantDto>> userTenantAuths =
          Optional.ofNullable(userTenantAuthorizations.getIfPresent(currentUserId.get()));
      if (userTenantAuths.isEmpty()) {
        repopulateCacheWithCurrentUserTenantAuthorization();
        userTenantAuths =
            Optional.ofNullable(userTenantAuthorizations.getIfPresent(currentUserId.get()));
      }
      return userTenantAuths.orElse(Collections.emptyList());
    } else {
      return Collections.singletonList(ZEEBE_DEFAULT_TENANT);
    }
  }

  private boolean isCurrentUserAuthorizedToSeeAllTenants(final List<String> tenantIds) {
    return tenantIds.stream().allMatch(this::isCurrentUserAuthorizedToSeeTenant);
  }

  private boolean isCurrentUserAuthorizedToSeeTenant(final String tenantId) {
    return getCurrentUserAuthorizedTenants().stream()
        .anyMatch(tenant -> Objects.equals(tenantId, tenant.getId()));
  }

  private void repopulateCacheWithCurrentUserTenantAuthorization() {
    getCurrentUserId()
        .ifPresent(id -> userTenantAuthorizations.put(id, fetchCurrentUserAuthorizedTenants()));
  }

  private List<TenantDto> fetchCurrentUserAuthorizedTenants() {
    return ccsmTokenService
        .getCurrentUserAuthToken()
        .map(ccsmTokenService::getAuthorizedTenantsFromToken)
        .orElse(Collections.emptyList());
  }

  private Optional<String> getCurrentUserId() {
    return ccsmTokenService.getCurrentUserIdFromAuthToken();
  }
}
