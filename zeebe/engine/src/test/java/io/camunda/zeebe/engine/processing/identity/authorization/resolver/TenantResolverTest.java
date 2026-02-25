/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.resolver;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantResolverTest {

  private MembershipState membershipState;
  private MappingRuleState mappingRuleState;
  private ClaimsExtractor claimsExtractor;
  private TenantResolver tenantResolver;

  @BeforeEach
  void setUp() {
    membershipState = mock(MembershipState.class);
    mappingRuleState = mock(MappingRuleState.class);
    claimsExtractor = new ClaimsExtractor(membershipState);
    tenantResolver = new TenantResolver(membershipState, mappingRuleState, claimsExtractor, true);
  }

  @Test
  void shouldReturnAnonymousAuthorizedTenantsWhenClaimsAreAnonymous() {
    // given
    final var claims = Map.<String, Object>of(AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then — isAnonymous() must return true so the ASSIGNED filter guard rejects the stream
    assertThat(authorizedTenants.isAnonymous()).isTrue();
  }

  @Test
  void shouldNotReturnAnonymousAuthorizedTenantsWhenClaimsAreAuthenticated() {
    // given
    final var username = "demo-user";
    final var tenantId = "tenant-1";
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.TENANT))
        .thenReturn(List.of(tenantId));
    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then
    assertThat(authorizedTenants.isAnonymous()).isFalse();
  }

  @Test
  void shouldReturnTrueWhenMultiTenancyDisabled() {
    // given - multi-tenancy disabled resolver
    final var resolverWithoutMt =
        new TenantResolver(membershipState, mappingRuleState, claimsExtractor, false);
    final var claims = Map.<String, Object>of();

    // when
    final var isAssigned = resolverWithoutMt.isAssignedToTenant(claims, "any-tenant");

    // then
    assertThat(isAssigned).isTrue();
  }

  @Test
  void shouldCheckTenantAssignmentForSpecificTenant() {
    // given - user assigned to tenant-1 and tenant-2
    final var username = "demo-user";
    final var tenant1 = "tenant-1";
    final var tenant2 = "tenant-2";
    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, "demo-user");
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.TENANT))
        .thenReturn(List.of(tenant1, tenant2));

    // when
    final var isAssignedToTenant1 = tenantResolver.isAssignedToTenant(claims, tenant1);
    final var isAssignedToTenant2 = tenantResolver.isAssignedToTenant(claims, tenant2);
    final var isAssignedToTenant3 = tenantResolver.isAssignedToTenant(claims, "non-existent");

    // then
    assertThat(isAssignedToTenant1).isTrue();
    assertThat(isAssignedToTenant2).isTrue();
    assertThat(isAssignedToTenant3).isFalse();
  }

  @Test
  void shouldResolveDirectTenantAssignmentForUser() {
    // given - user directly assigned to tenant
    final var username = "demo-user";
    final var tenantId = "tenant-1";
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.TENANT))
        .thenReturn(List.of(tenantId));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then
    assertThat(authorizedTenants.getAuthorizedTenantIds()).containsExactly(tenantId);
  }

  @Test
  void shouldResolveDirectTenantAssignmentForClient() {
    // given - client directly assigned to tenant
    final var clientId = "client-1";
    final var tenantId = "tenant-1";
    when(membershipState.getMemberships(EntityType.CLIENT, clientId, RelationType.TENANT))
        .thenReturn(List.of(tenantId));

    final var claims = Map.<String, Object>of(AUTHORIZED_CLIENT_ID, clientId);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then
    assertThat(authorizedTenants.getAuthorizedTenantIds()).containsExactly(tenantId);
  }

  @Test
  void shouldResolveTenantAssignmentViaRole() {
    // given - user assigned to role, role assigned to tenant
    final var username = "demo-user";
    final var roleId = "role-1";
    final var tenantId = "tenant-1";
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.ROLE))
        .thenReturn(List.of(roleId));
    when(membershipState.getMemberships(EntityType.ROLE, roleId, RelationType.TENANT))
        .thenReturn(List.of(tenantId));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then
    assertThat(authorizedTenants.getAuthorizedTenantIds()).containsExactly(tenantId);
  }

  @Test
  void shouldResolveTenantAssignmentViaGroup() {
    // given - user assigned to group, group assigned to tenant
    final var username = "demo-user";
    final var groupId = "group-1";
    final var tenantId = "tenant-1";
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
        .thenReturn(List.of(groupId));
    when(membershipState.getMemberships(EntityType.GROUP, groupId, RelationType.TENANT))
        .thenReturn(List.of(tenantId));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then
    assertThat(authorizedTenants.getAuthorizedTenantIds()).containsExactly(tenantId);
  }

  @Test
  void shouldResolveTenantAssignmentViaGroupRole() {
    // given - user in group, group has role, role assigned to tenant
    final var username = "demo-user";
    final var groupId = "group-1";
    final var roleId = "role-1";
    final var tenantId = "tenant-1";
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
        .thenReturn(List.of(groupId));
    when(membershipState.getMemberships(EntityType.GROUP, groupId, RelationType.ROLE))
        .thenReturn(List.of(roleId));
    when(membershipState.getMemberships(EntityType.ROLE, roleId, RelationType.TENANT))
        .thenReturn(List.of(tenantId));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then
    assertThat(authorizedTenants.getAuthorizedTenantIds()).containsExactly(tenantId);
  }

  @Test
  void shouldCombineTenantsFromMultipleSources() {
    // given - user has direct tenant, role tenant, and group tenant
    final var username = "demo-user";
    final var roleId = "role-1";
    final var groupId = "group-1";
    final var directTenant = "tenant-direct";
    final var roleTenant = "tenant-role";
    final var groupTenant = "tenant-group";

    // User memberships
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.TENANT))
        .thenReturn(List.of(directTenant));
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.ROLE))
        .thenReturn(List.of(roleId));
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
        .thenReturn(List.of(groupId));

    // Role memberships
    when(membershipState.getMemberships(EntityType.ROLE, roleId, RelationType.TENANT))
        .thenReturn(List.of(roleTenant));

    // Group memberships
    when(membershipState.getMemberships(EntityType.GROUP, groupId, RelationType.TENANT))
        .thenReturn(List.of(groupTenant));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var authorizedTenants = tenantResolver.getAuthorizedTenants(claims);

    // then
    assertThat(authorizedTenants.getAuthorizedTenantIds())
        .containsExactlyInAnyOrder(directTenant, roleTenant, groupTenant);
  }
}
