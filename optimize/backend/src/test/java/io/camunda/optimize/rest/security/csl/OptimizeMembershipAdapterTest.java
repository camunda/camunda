/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.core.port.out.MembershipPort.PrincipalType;
import io.camunda.security.core.port.out.MembershipQuery;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OptimizeMembershipAdapterTest {

  private final OptimizeMembershipAdapter membershipAdapter = new OptimizeMembershipAdapter();

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
}
