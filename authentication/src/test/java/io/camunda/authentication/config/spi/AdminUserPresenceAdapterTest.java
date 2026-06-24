/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.service.RoleServices;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AdminUserPresenceAdapterTest {

  private static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  private static final String USER_MEMBERS = "users";

  private final RoleServices roleServices = mock(RoleServices.class);
  private final InitializationConfiguration initializationConfiguration =
      new InitializationConfiguration();
  private final AdminUserPresenceAdapter port =
      new AdminUserPresenceAdapter(
          DefaultServiceRegistry.of(b -> b.roleServices("default", roleServices)),
          initializationConfiguration);

  @BeforeEach
  void setUp() {
    // Bind a plain request with no PT attribute — resolves to the "default" tenant.
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldReturnTrueWhenInitializationConfiguresAdminUser() {
    // given
    final var defaultRoles = new HashMap<>(initializationConfiguration.getDefaultRoles());
    defaultRoles.put(ADMIN_ROLE_ID, Map.of(USER_MEMBERS, Set.of("admin")));
    initializationConfiguration.setDefaultRoles(defaultRoles);

    // when / then
    assertThat(port.adminUserExists()).isTrue();
    // no live lookup is required when initialization config carries an admin user
    verifyNoInteractions(roleServices);
  }

  @Test
  void shouldReturnTrueWhenLiveStoreReportsAdminMember() {
    // given
    when(roleServices.hasMembersOfType(
            eq(ADMIN_ROLE_ID), eq(EntityType.USER), any(CamundaAuthentication.class)))
        .thenReturn(true);

    // when / then
    assertThat(port.adminUserExists()).isTrue();
  }

  @Test
  void shouldReturnFalseWhenNoConfiguredOrLiveAdminUser() {
    // given
    when(roleServices.hasMembersOfType(
            eq(ADMIN_ROLE_ID), eq(EntityType.USER), any(CamundaAuthentication.class)))
        .thenReturn(false);

    // when / then
    assertThat(port.adminUserExists()).isFalse();
  }

  @Test
  void shouldReturnTrueWhenLiveStoreThrows() {
    // given — mirror AdminUserCheckFilter: don't block traffic on transient failures
    when(roleServices.hasMembersOfType(
            eq(ADMIN_ROLE_ID), eq(EntityType.USER), any(CamundaAuthentication.class)))
        .thenThrow(new RuntimeException("secondary storage down"));

    // when / then
    assertThat(port.adminUserExists()).isTrue();
  }

  @Test
  void shouldRouteAdminCheckToRequestPhysicalTenant() {
    // given — two tenants, request scoped to "tenanta"
    final var defaultRoleServices = mock(RoleServices.class);
    final var tenantARoleServices = mock(RoleServices.class);
    final var adapter =
        new AdminUserPresenceAdapter(
            DefaultServiceRegistry.of(
                b ->
                    b.roleServices("default", defaultRoleServices)
                        .roleServices("tenanta", tenantARoleServices)),
            initializationConfiguration);
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    when(tenantARoleServices.hasMembersOfType(
            eq(ADMIN_ROLE_ID), eq(EntityType.USER), any(CamundaAuthentication.class)))
        .thenReturn(true);

    // when
    final var exists = adapter.adminUserExists();

    // then — admin presence resolved from the tenanta role services, not the default ones
    assertThat(exists).isTrue();
    verify(tenantARoleServices)
        .hasMembersOfType(eq(ADMIN_ROLE_ID), eq(EntityType.USER), any(CamundaAuthentication.class));
    verifyNoInteractions(defaultRoleServices);
  }
}
