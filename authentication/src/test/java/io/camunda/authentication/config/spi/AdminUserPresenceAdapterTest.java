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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.RoleServices;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminUserPresenceAdapterTest {

  private static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  private static final String USER_MEMBERS = "users";

  private final RoleServices roleServices = mock(RoleServices.class);
  private final SecurityConfiguration securityConfiguration = new SecurityConfiguration();
  private final AdminUserPresenceAdapter port =
      new AdminUserPresenceAdapter(roleServices, securityConfiguration);

  @Test
  void shouldReturnTrueWhenInitializationConfiguresAdminUser() {
    // given
    securityConfiguration
        .getInitialization()
        .getDefaultRoles()
        .put(ADMIN_ROLE_ID, Map.of(USER_MEMBERS, Set.of("admin")));

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
}
