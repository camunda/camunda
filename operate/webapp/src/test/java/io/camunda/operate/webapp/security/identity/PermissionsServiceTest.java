/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import static io.camunda.operate.webapp.security.identity.PermissionsService.RESOURCE_TYPE_DECISION_DEFINITION;
import static io.camunda.operate.webapp.security.identity.PermissionsService.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.IdentityProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.SecurityContextWrapper;
import io.camunda.operate.webapp.security.identity.PermissionsService.ResourcesAllowed;
import io.camunda.operate.webapp.security.sso.TokenAuthentication;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PermissionsServiceTest {

  private PermissionsService permissionsService;
  private OperateProperties mockOperateProperties;
  private SecurityContextWrapper mockSecurityContextWrapper;
  private IdentityProperties mockIdentityProperties;

  @BeforeEach
  public void setUp() {
    mockOperateProperties = mock(OperateProperties.class);
    mockSecurityContextWrapper = mock(SecurityContextWrapper.class);
    mockIdentityProperties = mock(IdentityProperties.class);

    when(mockIdentityProperties.isResourcePermissionsEnabled()).thenReturn(true);
    when(mockSecurityContextWrapper.getAuthentication()).thenReturn(null);

    permissionsService = new PermissionsService(mockOperateProperties, mockSecurityContextWrapper);
  }

  @Test
  public void testGetProcessDefinitionPermissionWithNullAuthentication() {
    final Set<String> res = permissionsService.getProcessDefinitionPermission("bpmnProcessId");
    assertThat(res.isEmpty()).isTrue();
  }

  @Test
  public void testGetProcessDefinitionPermission() {
    final IdentityAuthentication mockIdentityAuthentication = mock(IdentityAuthentication.class);
    final IdentityAuthorization mockIdentityAuthorization = mock(IdentityAuthorization.class);

    when(mockIdentityAuthorization.getResourceKey()).thenReturn("bpmnProcessId");
    when(mockIdentityAuthorization.getResourceType()).thenReturn(RESOURCE_TYPE_PROCESS_DEFINITION);
    when(mockIdentityAuthorization.getPermissions()).thenReturn(Set.of("testPermission"));
    when(mockIdentityAuthentication.getAuthorizations())
        .thenReturn(List.of(mockIdentityAuthorization));
    when(mockSecurityContextWrapper.getAuthentication()).thenReturn(mockIdentityAuthentication);

    final Set<String> res = permissionsService.getProcessDefinitionPermission("bpmnProcessId");

    assertThat(res).hasSize(1);
    assertThat(res.contains("testPermission")).isTrue();
  }

  @Test
  public void testGetDecisionDefinitionPermissionWithNullAuthentication() {

    final Set<String> res = permissionsService.getDecisionDefinitionPermission("decisionId");
    assertThat(res.isEmpty()).isTrue();
  }

  @Test
  public void testGetDecisionDefinitionPermission() {
    final TokenAuthentication mockTokenAuthentication = mock(TokenAuthentication.class);
    final IdentityAuthorization mockIdentityAuthorization = mock(IdentityAuthorization.class);

    when(mockIdentityAuthorization.getResourceKey()).thenReturn("decisionId");
    when(mockIdentityAuthorization.getResourceType()).thenReturn(RESOURCE_TYPE_DECISION_DEFINITION);
    when(mockIdentityAuthorization.getPermissions()).thenReturn(Set.of("testPermission"));
    when(mockTokenAuthentication.getAuthorizations())
        .thenReturn(List.of(mockIdentityAuthorization));
    when(mockSecurityContextWrapper.getAuthentication()).thenReturn(mockTokenAuthentication);

    final Set<String> res = permissionsService.getDecisionDefinitionPermission("decisionId");

    assertThat(res).hasSize(1);
    assertThat(res.contains("testPermission")).isTrue();
  }

  @Test
  public void testGetProcessesWithPermissionWithNullAuthentication() {
    when(mockOperateProperties.getIdentity()).thenReturn(mockIdentityProperties);

    final ResourcesAllowed res =
        permissionsService.getProcessesWithPermission(IdentityPermission.READ);
    final Set<String> resourceIds = res.getIds();
    assertThat(resourceIds.isEmpty()).isTrue();
  }

  @Test
  public void testGetDecisionsWithPermissionWithNullAuthentication() {
    when(mockOperateProperties.getIdentity()).thenReturn(mockIdentityProperties);

    final ResourcesAllowed res =
        permissionsService.getDecisionsWithPermission(IdentityPermission.READ);
    final Set<String> resourceIds = res.getIds();
    assertThat(resourceIds.isEmpty()).isTrue();
  }
}
