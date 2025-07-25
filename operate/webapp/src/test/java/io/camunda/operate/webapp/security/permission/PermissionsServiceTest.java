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

import io.camunda.operate.webapp.security.permission.PermissionsService.ResourcesAllowed;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PermissionsServiceTest {

  private SecurityConfiguration mockSecurityConfiguration;
  private AuthorizationsConfiguration mockAuthorizationsConfiguration;
  private AuthorizationChecker mockAuthorizationChecker;
  private CamundaAuthenticationProvider mockAuthenticationProvider;
  private ResourceAccessProvider mockResourceAccessProvider;

  private PermissionsService permissionsService;
  private final String username = "foo";
  private final String tenantId = "default";
  private final long roleKey = 456L;
  private final String roleId = "roleId";
  private final String groupId = "groupId";

  @BeforeEach
  public void setUp() {
    mockSecurityConfiguration = mock(SecurityConfiguration.class);
    mockAuthorizationsConfiguration = mock(AuthorizationsConfiguration.class);
    mockAuthorizationChecker = mock(AuthorizationChecker.class);
    mockAuthenticationProvider = mock(CamundaAuthenticationProvider.class);
    mockResourceAccessProvider = mock(ResourceAccessProvider.class);

    when(mockSecurityConfiguration.getAuthorizations()).thenReturn(mockAuthorizationsConfiguration);
    when(mockAuthorizationsConfiguration.isEnabled()).thenReturn(true);

    final var camundaUser = mock(CamundaAuthentication.class);
    when(camundaUser.authenticatedUsername()).thenReturn(username);
    when(camundaUser.authenticatedRoleIds()).thenReturn(List.of(roleId));
    when(camundaUser.authenticatedGroupIds()).thenReturn(List.of(groupId));
    when(camundaUser.authenticatedTenantIds()).thenReturn(List.of(tenantId));
    when(mockAuthenticationProvider.getCamundaAuthentication()).thenReturn(camundaUser);

    permissionsService =
        new PermissionsService(
            mockSecurityConfiguration,
            mockAuthorizationChecker,
            mockResourceAccessProvider,
            mockAuthenticationProvider);
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

    final CamundaAuthentication authentication =
        createCamundaAuthentication(username, List.of(tenantId), List.of(roleId), List.of(groupId));
    when(mockAuthenticationProvider.getCamundaAuthentication()).thenReturn(authentication);

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

    final CamundaAuthentication authentication =
        createCamundaAuthentication(username, List.of(tenantId), List.of(roleId), List.of(groupId));
    when(mockAuthenticationProvider.getCamundaAuthentication()).thenReturn(authentication);

    when(mockAuthorizationChecker.collectPermissionTypes(
            "decisionId", AuthorizationResourceType.DECISION_DEFINITION, authentication))
        .thenReturn(Set.of(PermissionType.READ));

    final Set<String> res = permissionsService.getDecisionDefinitionPermissions("decisionId");

    assertThat(res).hasSize(1);
    assertThat(res.contains(PermissionType.READ.name())).isTrue();
  }

  @Test
  public void testGetProcessesWithPermissionWithNullAuthentication() {
    when(mockAuthenticationProvider.getCamundaAuthentication()).thenReturn(null);
    final ResourcesAllowed res =
        permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION);
    final Set<String> resourceIds = res.getIds();
    assertThat(resourceIds.isEmpty()).isTrue();
  }

  @Test
  public void testGetDecisionsWithPermissionWithNullAuthentication() {
    when(mockAuthenticationProvider.getCamundaAuthentication()).thenReturn(null);
    final ResourcesAllowed res =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_INSTANCE);
    final Set<String> resourceIds = res.getIds();
    assertThat(resourceIds.isEmpty()).isTrue();
  }

  private CamundaAuthentication createCamundaAuthentication(
      final String username,
      final List<String> tenants,
      final List<String> roleIds,
      final List<String> groupIds) {
    return new CamundaAuthentication.Builder()
        .user(username)
        .tenants(tenants)
        .roleIds(roleIds)
        .groupIds(groupIds)
        .build();
  }
}
