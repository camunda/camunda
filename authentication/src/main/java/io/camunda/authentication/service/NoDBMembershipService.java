/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.security.oidc.OidcClaimExtractor;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Host-side {@link MembershipPort} for deployments without secondary storage. Only OIDC
 * groups-claim extraction is available; roles, tenants, and mapping rules always return empty since
 * no DB is available.
 */
@Service
@ConditionalOnSecondaryStorageDisabled
public class NoDBMembershipService implements MembershipPort {

  private final PhysicalTenantGroupsClaims groupsClaims;

  public NoDBMembershipService(
      final CamundaSecurityLibraryProperties cslProperties, final Environment environment) {
    groupsClaims = new PhysicalTenantGroupsClaims(cslProperties, environment);
  }

  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> groupIds(final MembershipQuery query) {
    final var tenantGroupsClaims = groupsClaims.current();
    if (!tenantGroupsClaims.groupsClaimConfigured()) {
      return List.of();
    }
    return OidcClaimExtractor.extractOrFallback(
            () -> tenantGroupsClaims.extract(query.tokenClaims()),
            List.<String>of(),
            "membership.groupIds.noDb")
        .stream()
        .distinct()
        .toList();
  }

  @Override
  public List<String> roleIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> tenantIds(final MembershipQuery query) {
    return List.of();
  }
}
