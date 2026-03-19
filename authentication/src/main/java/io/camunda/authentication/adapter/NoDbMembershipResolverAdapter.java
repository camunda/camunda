/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import io.camunda.gatekeeper.auth.OidcGroupsLoader;
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnSecondaryStorageDisabled
public final class NoDbMembershipResolverAdapter implements MembershipResolver {

  private final OidcGroupsLoader oidcGroupsLoader;
  private final boolean isGroupsClaimConfigured;

  public NoDbMembershipResolverAdapter(final AuthenticationConfig authenticationConfig) {
    if (authenticationConfig.oidc() != null) {
      oidcGroupsLoader = new OidcGroupsLoader(authenticationConfig.oidc().groupsClaim());
      isGroupsClaimConfigured = authenticationConfig.oidc().isGroupsClaimConfigured();
    } else {
      oidcGroupsLoader = new OidcGroupsLoader(null);
      isGroupsClaimConfigured = false;
    }
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final Set<String> groups =
        isGroupsClaimConfigured
            ? new HashSet<>(oidcGroupsLoader.load(tokenClaims))
            : Collections.emptySet();

    return CamundaAuthentication.of(
        a -> {
          if (principalType == PrincipalType.CLIENT) {
            a.clientId(principalId);
          } else {
            a.user(principalId);
          }
          return a.roleIds(Collections.emptyList())
              .groupIds(groups.stream().toList())
              .mappingRule(Collections.emptyList())
              .tenants(Collections.emptyList())
              .claims(tokenClaims);
        });
  }
}
