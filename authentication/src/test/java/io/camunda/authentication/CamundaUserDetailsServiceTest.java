/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsServiceTest {

  private static final String TEST_USER_ID = "username1";

  @Mock private UserServices userService;
  @Mock private AuthorizationServices authorizationServices;
  @Mock private RoleServices roleServices;
  @Mock private TenantServices tenantServices;
  @Mock private GroupServices groupServices;
  private CamundaUserDetailsService userDetailsService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    userDetailsService =
        new CamundaUserDetailsService(
            userService, authorizationServices, roleServices, tenantServices, groupServices);
    when(userService.withAuthentication(any(CamundaAuthentication.class))).thenReturn(userService);
    when(authorizationServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authorizationServices);
    when(roleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(roleServices);
    when(tenantServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(tenantServices);
    when(groupServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(groupServices);
  }

  @Test
  public void testUserDetailsIsLoaded() {
    // given
    when(userService.search(any()))
        .thenReturn(
            new SearchQueryResult<>(
                1,
                false,
                List.of(new UserEntity(100L, TEST_USER_ID, "Foo Bar", "email@tested", "password1")),
                null,
                null));

    final var roleId = "admin";
    when(authorizationServices.getAuthorizedApplications(
            Map.of(
                EntityType.USER,
                Set.of(TEST_USER_ID),
                EntityType.ROLE,
                Set.of(roleId, "roleGroup"))))
        .thenReturn(List.of("operate", "identity"));
    final var adminGroup = new GroupEntity(1L, "admin", "Admin Group", "description");
    when(groupServices.getGroupsByMemberId(TEST_USER_ID, EntityType.USER))
        .thenReturn(List.of(adminGroup));

    final var adminRole = new RoleEntity(2L, roleId, "ADMIN", "description");
    final var groupRole = new RoleEntity(3L, "roleGroup", "Role Group", "description");
    final var adminTenant = new TenantEntity(100L, "tenant1", "Tenant One", "First Tenant");
    final var groupTenant = new TenantEntity(200L, "tenant1", "Tenant One", "First Tenant");
    when(roleServices.getRolesByUserAndGroups(TEST_USER_ID, Set.of(adminGroup.groupId())))
        .thenReturn(List.of(adminRole, groupRole));
    when(tenantServices.getTenantsByUserAndGroupsAndRoles(
            TEST_USER_ID, Set.of(adminGroup.groupId()), Set.of(roleId, groupRole.roleId())))
        .thenReturn(List.of(adminTenant, groupTenant));

    // when
    final CamundaUser user = (CamundaUser) userDetailsService.loadUserByUsername(TEST_USER_ID);

    // then
    assertThat(user).isInstanceOf(CamundaUser.class);
    assertThat(user.getUserKey()).isEqualTo(100L);
    assertThat(user.getName()).isEqualTo("Foo Bar");
    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(user.getPassword()).isEqualTo("password1");
    assertThat(user.getEmail()).isEqualTo("email@tested");
    assertThat(user.getAuthenticationContext().username()).isEqualTo(TEST_USER_ID);
    assertThat(user.getAuthenticationContext().authorizedApplications())
        .containsExactlyInAnyOrder("operate", "identity");
    assertThat(user.getAuthenticationContext().roles())
        .isEqualTo(List.of(adminRole.roleId(), groupRole.roleId()));
    assertThat(user.getAuthenticationContext().groups()).isEqualTo(List.of(adminGroup.groupId()));
    assertThat(user.getAuthenticationContext().tenants())
        .isEqualTo(List.of(TenantDTO.fromEntity(adminTenant), TenantDTO.fromEntity(groupTenant)));
  }

  @Test
  public void testUserDetailsNotFound() {
    // given
    when(userService.search(any()))
        .thenReturn(new SearchQueryResult<>(0, false, Collections.emptyList(), null, null));
    // when/then
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(TEST_USER_ID))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
