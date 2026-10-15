/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.repository.AccessRepository;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizeAccessResolutionTest {

  private final AccessRepository access = mock(AccessRepository.class);
  private final TenantMembershipRepository tenants = mock(TenantMembershipRepository.class);
  private final OptimizeMembershipAdapter adapter =
      new OptimizeMembershipAdapter(tenants, access, Duration.ZERO);

  private MembershipQuery query(final Map<String, Object> claims, final String principal) {
    return new MembershipQuery(claims, principal, PrincipalType.USER);
  }

  @Test
  void shouldMatchMappingRulesOnScalarAndListClaims() {
    // given
    when(access.findAllMappingRules())
        .thenReturn(
            List.of(
                new AccessMappingRuleDto("by-name", "preferred_username", "demo"),
                new AccessMappingRuleDto("by-group", "groups", "optimize-admins"),
                new AccessMappingRuleDto("other", "preferred_username", "nobody")));

    // when
    final var ruleIds =
        adapter.mappingRuleIds(
            query(
                Map.of("preferred_username", "demo", "groups", List.of("x", "optimize-admins")),
                "demo"));

    // then
    assertThat(ruleIds).containsExactlyInAnyOrder("by-name", "by-group");
  }

  @Test
  void shouldGiveNoRolesWhenNoRuleMatches() {
    // given
    final var role = new AccessRoleDto("admin", "Admin");
    role.setMappingRuleIds(List.of("by-name"));
    when(access.findAllRoles()).thenReturn(List.of(role));

    // when / then
    assertThat(adapter.roleIds(query(Map.of(), "bob"))).isEmpty();
  }

  @Test
  void shouldResolveRolesFromRulesAndDirectUserAssignment() {
    // given
    final var viaRule = new AccessRoleDto("admin", "Admin");
    viaRule.setMappingRuleIds(List.of("by-name"));
    final var direct = new AccessRoleDto("viewer", "Viewer");
    direct.setUserIds(List.of("carol"));
    when(access.findAllRoles()).thenReturn(List.of(viaRule, direct));

    // when
    final var byRule =
        adapter.roleIds(query(Map.of(), "demo").withMappingRuleIds(List.of("by-name")));
    final var byUser = adapter.roleIds(query(Map.of(), "carol"));

    // then
    assertThat(byRule).containsExactly("admin");
    assertThat(byUser).containsExactly("viewer");
  }

  @Test
  void shouldResolveTenantsFromRolesAndMappingRules() {
    // given
    when(tenants.findTenantIds(MemberType.ROLE, Set.of("admin"))).thenReturn(Set.of("t-role"));
    when(tenants.findTenantIds(MemberType.MAPPING_RULE, Set.of("by-name")))
        .thenReturn(Set.of("t-rule"));

    // when
    final var tenantIds =
        adapter.tenantIds(
            query(Map.of(), "demo")
                .withMappingRuleIds(List.of("by-name"))
                .withRoleIds(List.of("admin")));

    // then
    assertThat(tenantIds).containsExactlyInAnyOrder("t-role", "t-rule");
  }

  @Test
  void shouldAllowAccessOnlyWithARole() {
    // given
    final var policy = new OptimizeRoleComponentAccessPolicy();

    // when / then
    assertThat(
            policy
                .checkAccess(
                    CamundaAuthentication.of(b -> b.user("demo").roleIds(List.of("admin"))))
                .isRight())
        .isTrue();
    assertThat(policy.checkAccess(CamundaAuthentication.of(b -> b.user("bob"))).isLeft()).isTrue();
  }

  @Test
  void shouldLetOnlyAdminsAdminister() {
    // given
    final var provider = mock(CamundaAuthenticationProvider.class);
    final var guard = new OptimizeAdminGuard(provider);
    when(provider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("carol").roleIds(List.of("viewer"))));

    // when / then
    assertThatThrownBy(guard::requireAdmin).isInstanceOf(ForbiddenException.class);

    when(provider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("demo").roleIds(List.of("admin"))));
    guard.requireAdmin();
  }
}
