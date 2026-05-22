/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.auth.MembershipPort.PrincipalType;
import io.camunda.security.api.model.auth.MembershipQuery;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoDBMembershipServiceTest {

  private static MembershipQuery query(final Map<String, Object> claims) {
    return new MembershipQuery(claims, "alice", PrincipalType.USER);
  }

  @Test
  void groupIdsExtractsFromClaimsWhenConfigured() {
    final var service = serviceWithGroupsClaim("$.groups");
    assertThat(service.groupIds(query(Map.of("sub", "alice", "groups", List.of("eng", "ops")))))
        .containsExactlyInAnyOrder("eng", "ops");
  }

  @Test
  void groupIdsReturnsEmptyWhenNoGroupsClaimConfigured() {
    final var service = new NoDBMembershipService(new SecurityConfiguration());
    assertThat(service.groupIds(query(Map.of("groups", List.of("eng"))))).isEmpty();
  }

  @Test
  void allOtherMethodsReturnEmpty() {
    final var service = new NoDBMembershipService(new SecurityConfiguration());
    final var q = query(Map.of());
    assertThat(service.mappingRuleIds(q)).isEmpty();
    assertThat(service.roleIds(q)).isEmpty();
    assertThat(service.tenantIds(q)).isEmpty();
  }

  private static NoDBMembershipService serviceWithGroupsClaim(final String claim) {
    final var config = new SecurityConfiguration();
    config.getAuthentication().getOidc().setGroupsClaim(claim);
    return new NoDBMembershipService(config);
  }
}
