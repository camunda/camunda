/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoDBMembershipServiceTest {

  @Test
  void providerReturnsGroupsFromClaimsWhenConfigured() {
    final var config = securityConfigurationWithGroupsClaim("$.groups");
    final var service = new NoDBMembershipService(config);

    final var provider =
        service.createProvider(
            Map.of("sub", "alice", "groups", List.of("eng", "ops")), "alice", PrincipalType.USER);

    assertThat(provider.groups()).containsExactlyInAnyOrder("eng", "ops");
    assertThat(provider.roles()).isEmpty();
    assertThat(provider.tenants()).isEmpty();
    assertThat(provider.mappingRules()).isEmpty();
  }

  @Test
  void providerReturnsEmptyGroupsWhenNoGroupsClaimConfigured() {
    final var config = new SecurityConfiguration();
    final var service = new NoDBMembershipService(config);

    final var provider =
        service.createProvider(
            Map.of("sub", "alice", "groups", List.of("eng", "ops")), "alice", PrincipalType.USER);

    assertThat(provider.groups()).isEmpty();
    assertThat(provider.roles()).isEmpty();
    assertThat(provider.tenants()).isEmpty();
    assertThat(provider.mappingRules()).isEmpty();
  }

  @Test
  void providerForUserReturnsEmptyMemberships() {
    final var service = new NoDBMembershipService(new SecurityConfiguration());

    final var provider = service.createProviderForUser("alice");

    assertThat(provider.groups()).isEmpty();
    assertThat(provider.roles()).isEmpty();
    assertThat(provider.tenants()).isEmpty();
    assertThat(provider.mappingRules()).isEmpty();
  }

  private static SecurityConfiguration securityConfigurationWithGroupsClaim(
      final String groupsClaim) {
    final var config = new SecurityConfiguration();
    config.getAuthentication().getOidc().setGroupsClaim(groupsClaim);
    return config;
  }
}
