/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.auth.Memberships;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoDBMembershipServiceTest {

  @Test
  void resolveMembershipsReturnsGroupsFromClaimsWhenConfigured() {
    final var config = securityConfigurationWithGroupsClaim("$.groups");
    final var service = new NoDBMembershipService(config);

    final var memberships =
        service.resolveMemberships(
            Map.of("sub", "alice", "groups", List.of("eng", "ops")),
            "alice",
            PrincipalType.USER);

    assertThat(memberships.groupIds()).containsExactlyInAnyOrder("eng", "ops");
    assertThat(memberships.roleIds()).isEmpty();
    assertThat(memberships.tenantIds()).isEmpty();
    assertThat(memberships.mappingRuleIds()).isEmpty();
  }

  @Test
  void resolveMembershipsReturnsEmptyGroupsWhenNoGroupsClaimConfigured() {
    final var config = new SecurityConfiguration();
    final var service = new NoDBMembershipService(config);

    final var memberships =
        service.resolveMemberships(
            Map.of("sub", "alice", "groups", List.of("eng", "ops")),
            "alice",
            PrincipalType.USER);

    assertThat(memberships.groupIds()).isEmpty();
    assertThat(memberships.roleIds()).isEmpty();
    assertThat(memberships.tenantIds()).isEmpty();
    assertThat(memberships.mappingRuleIds()).isEmpty();
  }

  @Test
  void resolveMembershipsForUserReturnsEmptyMemberships() {
    final var service = new NoDBMembershipService(new SecurityConfiguration());

    final var memberships = service.resolveMembershipsForUser("alice");

    assertThat(memberships).isEqualTo(Memberships.empty());
  }

  private static SecurityConfiguration securityConfigurationWithGroupsClaim(
      final String groupsClaim) {
    final var config = new SecurityConfiguration();
    config.getAuthentication().getOidc().setGroupsClaim(groupsClaim);
    return config;
  }
}
