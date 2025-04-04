/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaUser;
import io.camunda.operate.webapp.security.permission.PermissionsService.ResourcesAllowed;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class PermissionsServiceTest {

  private SecurityConfiguration mockSecurityConfiguration;
  private AuthorizationsConfiguration mockAuthorizationsConfiguration;
  private AuthorizationChecker mockAuthorizationChecker;
  private TenantService mockTenantService;
  private Authentication mockAuthentication;

  private PermissionsService permissionsService;
  private final String username = "foo";
  private final String tenantId = "default";
  private final long roleKey = 456L;

  @BeforeEach
  public void setUp() {
    mockSecurityConfiguration = mock(SecurityConfiguration.class);
    mockAuthorizationsConfiguration = mock(AuthorizationsConfiguration.class);
    mockAuthorizationChecker = mock(AuthorizationChecker.class);
    mockTenantService = mock(TenantService.class);
    mockAuthentication = mock(Authentication.class);

    when(mockSecurityConfiguration.getAuthorizations()).thenReturn(mockAuthorizationsConfiguration);
    when(mockAuthorizationsConfiguration.isEnabled()).thenReturn(true);

    final CamundaUser camundaUser = mock(CamundaUser.class);
    when(camundaUser.getUsername()).thenReturn(username);
    when(camundaUser.getAuthenticationContext())
        .thenReturn(
            new AuthenticationContext(
                "test",
                List.of(new RoleEntity(roleKey, "roleName")),
                List.of(),
                List.of(new TenantDTO(123L, tenantId, "tenantName", "")),
                List.of()));
    when(mockAuthentication.getPrincipal()).thenReturn(camundaUser);
    final SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(mockAuthentication);
    SecurityContextHolder.setContext(securityContext);

    permissionsService =
        new PermissionsService(
            mockSecurityConfiguration, mockAuthorizationChecker, mockTenantService);
  }

  @Test
  public void testPermissionsEnabled() {
    final boolean enabled = permissionsService.permissionsEnabled();
    assertThat(enabled).isTrue();
  }

  @Test
  public void testGetProcessDefinitionPermissionWithNullAuthentication() {
    final Set<String> res = permissionsService.getProcessDefinitionPermissions("bpmnProcessId");
    assertThat(res.isEmpty()).isTrue();
  }

  @Test
  public void testGetProcessDefinitionPermission() {

    final io.camunda.security.auth.Authentication authentication =
        createCamundaAuthentication(username, List.of(tenantId), List.of(roleKey));

    when(mockAuthorizationChecker.collectPermissionTypes(
            "bpmnProcessId", AuthorizationResourceType.PROCESS_DEFINITION, authentication))
        .thenReturn(Set.of(PermissionType.DELETE_PROCESS));
    final Set<String> res = permissionsService.getProcessDefinitionPermissions("bpmnProcessId");

    assertThat(res).hasSize(1);
    assertThat(res.contains(PermissionType.DELETE_PROCESS.name())).isTrue();
  }

  @Test
  public void testGetDecisionDefinitionPermissionWithNullAuthentication() {

    final Set<String> res = permissionsService.getDecisionDefinitionPermissions("decisionId");
    assertThat(res.isEmpty()).isTrue();
  }

  @Test
  public void testGetDecisionDefinitionPermission() {

    final io.camunda.security.auth.Authentication authentication =
        createCamundaAuthentication(username, List.of(tenantId), List.of(roleKey));

    when(mockAuthorizationChecker.collectPermissionTypes(
            "decisionId", AuthorizationResourceType.DECISION_DEFINITION, authentication))
        .thenReturn(Set.of(PermissionType.READ));

    final Set<String> res = permissionsService.getDecisionDefinitionPermissions("decisionId");

    assertThat(res).hasSize(1);
    assertThat(res.contains(PermissionType.READ.name())).isTrue();
  }

  @Test
  public void testGetProcessesWithPermissionWithNullAuthentication() {
    final ResourcesAllowed res =
        permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION);
    final Set<String> resourceIds = res.getIds();
    assertThat(resourceIds.isEmpty()).isTrue();
  }

  @Test
  public void testGetDecisionsWithPermissionWithNullAuthentication() {
    final ResourcesAllowed res =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_INSTANCE);
    final Set<String> resourceIds = res.getIds();
    assertThat(resourceIds.isEmpty()).isTrue();
  }

  private io.camunda.security.auth.Authentication createCamundaAuthentication(
      final String username, final List<String> tenants, final List<Long> roleKeys) {
    return new io.camunda.security.auth.Authentication.Builder()
        .user(username)
        .tenants(tenants)
        .roleKeys(roleKeys)
        .build();
  }
}
