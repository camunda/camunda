/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import io.camunda.security.core.auth.MappingRuleMatcher;
import io.camunda.security.core.auth.MappingRuleMatcher.MappingRule;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.core.oidc.OidcPrincipalLoader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Grants {@link ClusterAdminSecurityConfiguration#CLUSTER_ADMIN_AUTHORITY} to a validated bearer
 * token when it matches a configured cluster-admin OIDC claim: client id, group, or name/value.
 *
 * <p>The resulting {@link JwtAuthenticationToken} is named after the resolved client id, or the
 * token subject if unavailable, so {@code ClusterAdminAuthenticationConverter} creates a
 * client-based principal.
 */
public final class ClusterAdminJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final Collection<GrantedAuthority> CLUSTER_ADMIN_AUTHORITIES =
      List.of(
          new SimpleGrantedAuthority(ClusterAdminSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY));

  private final Set<String> clients;
  private final Set<String> groups;
  private final List<MappingRule> claimRules;
  private final OidcPrincipalLoader principalLoader;
  private final OidcGroupsExtractor groupsExtractor;

  public ClusterAdminJwtAuthenticationConverter(
      final ClusterAdminOidcProperties properties,
      final String clientIdClaim,
      final String groupsClaim) {
    clients = Set.copyOf(properties.clients());
    groups = Set.copyOf(properties.groups());
    claimRules =
        properties.claims().stream()
            .map(claim -> (MappingRule) new ClaimRule(claim.name(), claim.value()))
            .toList();
    principalLoader = new OidcPrincipalLoader(null, clientIdClaim);
    groupsExtractor = new OidcGroupsExtractor(groupsClaim);
  }

  @Override
  public AbstractAuthenticationToken convert(final Jwt jwt) {
    final Map<String, Object> claims = jwt.getClaims();
    final String clientId = principalLoader.load(claims).clientId();
    final boolean isClusterAdmin =
        matchesClient(clientId) || matchesGroup(claims) || matchesClaim(claims);

    final String name = clientId != null ? clientId : jwt.getSubject();
    final Collection<GrantedAuthority> authorities =
        isClusterAdmin ? CLUSTER_ADMIN_AUTHORITIES : List.of();
    return new JwtAuthenticationToken(jwt, authorities, name);
  }

  private boolean matchesClient(final String clientId) {
    return clientId != null && clients.contains(clientId);
  }

  private boolean matchesGroup(final Map<String, Object> claims) {
    if (groups.isEmpty()) {
      return false;
    }
    final List<String> tokenGroups = groupsExtractor.extract(claims);
    return tokenGroups != null && tokenGroups.stream().anyMatch(groups::contains);
  }

  private boolean matchesClaim(final Map<String, Object> claims) {
    return !claimRules.isEmpty()
        && MappingRuleMatcher.matchingRules(claimRules.stream(), claims).findAny().isPresent();
  }

  /**
   * Adapts a configured {@link ClusterAdminClaim} to CSL's {@link MappingRule} matcher contract.
   */
  private record ClaimRule(String claimName, String claimValue) implements MappingRule {
    @Override
    public String mappingRuleId() {
      return claimName;
    }
  }
}
