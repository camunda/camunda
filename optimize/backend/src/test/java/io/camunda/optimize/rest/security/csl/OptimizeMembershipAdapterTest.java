/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OptimizeMembershipAdapterTest {

  private final OptimizeMembershipAdapter membershipAdapter =
      new OptimizeMembershipAdapter(null, null, Duration.ZERO);

  @ParameterizedTest
  @EnumSource(PrincipalType.class)
  void shouldReturnEmptyMembershipForAnyPrincipal(final PrincipalType principalType) {
    // given
    final MembershipQuery query =
        new MembershipQuery(Map.of("sub", "someone"), "someone", principalType);

    // when / then
    assertThat(membershipAdapter.mappingRuleIds(query)).isEmpty();
    assertThat(membershipAdapter.groupIds(query)).isEmpty();
    assertThat(membershipAdapter.roleIds(query)).isEmpty();
    assertThat(membershipAdapter.tenantIds(query)).isEmpty();
  }

  @Test
  void shouldUnionDirectAndGroupTenantAssignments() {
    // given
    final var repository = mock(TenantMembershipRepository.class);
    when(repository.findTenantIds(MemberType.USER, Set.of("alice"))).thenReturn(Set.of("t1"));
    when(repository.findTenantIds(MemberType.GROUP, Set.of("g1"))).thenReturn(Set.of("t1", "t2"));
    final var adapter = new OptimizeMembershipAdapter(repository, null, Duration.ZERO);
    final var query =
        new MembershipQuery(
            Map.of(), "alice", PrincipalType.USER, List.of(), List.of("g1"), List.of());

    // when
    final var tenantIds = adapter.tenantIds(query);

    // then
    assertThat(tenantIds).containsExactlyInAnyOrder("t1", "t2");
  }

  @Test
  void shouldLookUpClientAssignmentsForClientPrincipals() {
    // given
    final var repository = mock(TenantMembershipRepository.class);
    when(repository.findTenantIds(MemberType.CLIENT, Set.of("svc"))).thenReturn(Set.of("t9"));
    final var adapter = new OptimizeMembershipAdapter(repository, null, Duration.ZERO);
    final var query = new MembershipQuery(Map.of(), "svc", PrincipalType.CLIENT);

    // when / then
    assertThat(adapter.tenantIds(query)).containsExactly("t9");
  }
}
