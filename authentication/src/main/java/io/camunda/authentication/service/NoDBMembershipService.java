/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.api.model.auth.MembershipPort;
import io.camunda.security.api.model.auth.MembershipQuery;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Host-side {@link MembershipPort} for deployments without secondary storage. Only OIDC
 * groups-claim extraction is available; roles, tenants, and mapping rules always return empty since
 * no DB is available.
 */
@Service
@ConditionalOnSecondaryStorageDisabled
public class NoDBMembershipService implements MembershipPort {

  private final OidcGroupsExtractor oidcGroupsExtractor;
  private final boolean isGroupsClaimConfigured;

  public NoDBMembershipService(final SecurityConfiguration securityConfiguration) {
    oidcGroupsExtractor =
        new OidcGroupsExtractor(
            securityConfiguration.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured =
        securityConfiguration.getAuthentication().getOidc().isGroupsClaimConfigured();
  }

  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> groupIds(final MembershipQuery query) {
    if (!isGroupsClaimConfigured) {
      return List.of();
    }
    final var extracted = oidcGroupsExtractor.extract(query.tokenClaims());
    return extracted != null ? extracted.stream().distinct().toList() : List.of();
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
