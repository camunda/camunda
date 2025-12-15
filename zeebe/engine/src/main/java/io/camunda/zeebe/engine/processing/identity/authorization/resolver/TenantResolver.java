/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.resolver;

import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.zeebe.engine.processing.identity.AuthenticatedAuthorizedTenants;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Resolves tenant assignments for entities. */
public final class TenantResolver {

  private final MembershipState membershipState;
  private final MappingRuleState mappingRuleState;
  private final ClaimsExtractor claimsExtractor;
  private final boolean multiTenancyEnabled;

  public TenantResolver(
      final MembershipState membershipState,
      final MappingRuleState mappingRuleState,
      final ClaimsExtractor claimsExtractor,
      final boolean multiTenancyEnabled) {
    this.membershipState = membershipState;
    this.mappingRuleState = mappingRuleState;
    this.claimsExtractor = claimsExtractor;
    this.multiTenancyEnabled = multiTenancyEnabled;
  }

  /**
   * Checks if a user is assigned to a specific tenant. If multi-tenancy is disabled, this method
   * will always return true.
   *
   * @param claims the authorization claims map
   * @param tenantId the tenant we want to check assignment for
   * @return true if assigned or multi-tenancy is disabled, false otherwise
   */
  public boolean isAssignedToTenant(final Map<String, Object> claims, final String tenantId) {
    if (!multiTenancyEnabled) {
      return true;
    }
    return getAuthorizedTenants(claims).isAuthorizedForTenantId(tenantId);
  }

  /**
   * Gets all authorized tenants for the given claims. Includes tenants from user/client, groups,
   * roles, and mapping rules.
   *
   * @param authorizations the authorization claims map
   * @return an {@link AuthorizedTenants} instance with the authorized tenant IDs
   */
  public AuthorizedTenants getAuthorizedTenants(final Map<String, Object> authorizations) {
    if (claimsExtractor.isAuthorizedAnonymousUser(authorizations)) {
      return AuthorizedTenants.ANONYMOUS;
    }

    if (!multiTenancyEnabled) {
      return AuthorizedTenants.DEFAULT_TENANTS;
    }

    final var authorizedTenants = new HashSet<String>();
    claimsExtractor
        .getUsername(authorizations)
        .ifPresent(
            username ->
                authorizedTenants.addAll(
                    getTenantIdsForEntity(authorizations, EntityType.USER, username)
                        .collect(Collectors.toSet())));

    claimsExtractor
        .getClientId(authorizations)
        .ifPresent(
            clientId ->
                authorizedTenants.addAll(
                    getTenantIdsForEntity(authorizations, EntityType.CLIENT, clientId)
                        .collect(Collectors.toSet())));

    final var tenantsOfMappingRule =
        getPersistedMappingRules(authorizations)
            .flatMap(
                mappingRule ->
                    getTenantIdsForEntity(
                        authorizations, EntityType.MAPPING_RULE, mappingRule.getMappingRuleId()))
            .collect(Collectors.toSet());
    authorizedTenants.addAll(tenantsOfMappingRule);

    return new AuthenticatedAuthorizedTenants(authorizedTenants);
  }

  /**
   * Gets tenant IDs for a specific entity including inherited assignments.
   *
   * <p>This includes:
   *
   * <ul>
   *   <li>Direct tenant assignments to the entity
   *   <li>Tenant assignments via roles the entity belongs to
   *   <li>Tenant assignments via groups the entity belongs to
   *   <li>Tenant assignments via roles that groups belong to
   * </ul>
   *
   * @param authorizations the authorization claims map
   * @param entityType the type of the entity
   * @param entityId the ID of the entity
   * @return a stream of tenant IDs
   */
  public Stream<String> getTenantIdsForEntity(
      final Map<String, Object> authorizations,
      final EntityType entityType,
      final String entityId) {
    return Stream.concat(
        membershipState.getMemberships(entityType, entityId, RelationType.TENANT).stream(),
        Stream.concat(
            claimsExtractor.getGroups(authorizations, entityType, entityId).stream()
                .flatMap(
                    groupId ->
                        Stream.concat(
                            membershipState
                                .getMemberships(EntityType.GROUP, groupId, RelationType.TENANT)
                                .stream(),
                            membershipState
                                .getMemberships(EntityType.GROUP, groupId, RelationType.ROLE)
                                .stream()
                                .flatMap(
                                    roleId ->
                                        membershipState
                                            .getMemberships(
                                                EntityType.ROLE, roleId, RelationType.TENANT)
                                            .stream()))),
            membershipState.getMemberships(entityType, entityId, RelationType.ROLE).stream()
                .flatMap(
                    roleId ->
                        membershipState
                            .getMemberships(EntityType.ROLE, roleId, RelationType.TENANT)
                            .stream())));
  }

  private Stream<PersistedMappingRule> getPersistedMappingRules(
      final Map<String, Object> authorizations) {
    final var claims = claimsExtractor.getTokenClaims(authorizations);
    return MappingRuleMatcher.matchingRules(mappingRuleState.getAll().stream(), claims);
  }
}
