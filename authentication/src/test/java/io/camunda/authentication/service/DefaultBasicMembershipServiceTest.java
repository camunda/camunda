/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.RoleEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DefaultBasicMembershipServiceTest {

  @Mock private TenantServices tenantServices;
  @Mock private RoleServices roleServices;
  @Mock private GroupServices groupServices;

  private DefaultBasicMembershipService membershipService;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());
    when(tenantServices.getTenantsByMemberTypeAndMemberIds(any(), any())).thenReturn(List.of());

    membershipService =
        new DefaultBasicMembershipService(tenantServices, roleServices, groupServices);
  }

  @Test
  void shouldNotInvokeAnyServiceUntilResolverIsRead() {
    // given
    membershipService.newResolver("demo");

    // then — constructing the resolver alone does not trigger DB calls
    verify(groupServices, never()).getGroupsByMemberTypeAndMemberIds(any(), any());
    verify(roleServices, never()).getRolesByMemberTypeAndMemberIds(any(), any());
    verify(tenantServices, never()).getTenantsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldMemoizeMembershipLookups() {
    // given
    when(roleServices.getRolesByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(List.of(new RoleEntity(1L, "role1", "role", "desc")));
    final var resolver = membershipService.newResolver("demo");

    // when — read roles twice and groups three times (groups is a prerequisite of roles)
    resolver.roles();
    resolver.roles();
    resolver.groups();
    resolver.groups();
    resolver.groups();

    // then — each underlying service is called exactly once
    verify(groupServices).getGroupsByMemberTypeAndMemberIds(any(), any());
    verify(roleServices).getRolesByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void mappingRulesAlwaysEmptyForBasicAuth() {
    // given
    final var resolver = membershipService.newResolver("demo");

    // then — BASIC auth has no token claims, so mapping rules are always empty without any DB call
    assertThat(resolver.mappingRules()).isEmpty();
  }
}
