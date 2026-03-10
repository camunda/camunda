/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.RoleEntity;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcRoleManagementAdapterTest {

  private final RoleServices roleServices = mock(RoleServices.class);
  private final RoleServices authenticatedRoleServices = mock(RoleServices.class);
  private final CamundaAuthenticationProvider authProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CamundaAuthentication authentication = CamundaAuthentication.none();

  private OcRoleManagementAdapter adapter;

  @BeforeEach
  void setUp() {
    when(authProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(roleServices.withAuthentication(authentication)).thenReturn(authenticatedRoleServices);
    adapter = new OcRoleManagementAdapter(roleServices, authProvider);
  }

  @Test
  void getByIdDelegatesToRoleServicesGetRole() {
    final var entity = new RoleEntity(10L, "admin", "Administrator", "Admin role");
    when(authenticatedRoleServices.getRole("admin")).thenReturn(entity);

    final var result = adapter.getById("admin");

    assertThat(result.roleKey()).isEqualTo(10L);
    assertThat(result.roleId()).isEqualTo("admin");
    assertThat(result.name()).isEqualTo("Administrator");
    assertThat(result.description()).isEqualTo("Admin role");
  }

  @Test
  void createDelegatesToRoleServicesCreateRole() {
    when(authenticatedRoleServices.createRole(any(CreateRoleRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var result = adapter.create("editor", "Editor", "Editor role");

    assertThat(result.roleId()).isEqualTo("editor");
    assertThat(result.name()).isEqualTo("Editor");

    final var captor = ArgumentCaptor.forClass(CreateRoleRequest.class);
    verify(authenticatedRoleServices).createRole(captor.capture());
    assertThat(captor.getValue().roleId()).isEqualTo("editor");
  }

  @Test
  void deleteDelegatesToRoleServicesDeleteRole() {
    when(authenticatedRoleServices.deleteRole("admin"))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.delete("admin");

    verify(authenticatedRoleServices).deleteRole("admin");
  }

  @Test
  void addMemberMapsEntityTypeCorrectly() {
    when(authenticatedRoleServices.addMember(any(RoleMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.addMember("admin", "alice", MemberType.USER);

    final var captor = ArgumentCaptor.forClass(RoleMemberRequest.class);
    verify(authenticatedRoleServices).addMember(captor.capture());
    assertThat(captor.getValue().roleId()).isEqualTo("admin");
    assertThat(captor.getValue().entityId()).isEqualTo("alice");
    assertThat(captor.getValue().entityType()).isEqualTo(EntityType.USER);
  }

  @Test
  void removeMemberDelegatesToRoleServices() {
    when(authenticatedRoleServices.removeMember(any(RoleMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.removeMember("admin", "alice", MemberType.GROUP);

    final var captor = ArgumentCaptor.forClass(RoleMemberRequest.class);
    verify(authenticatedRoleServices).removeMember(captor.capture());
    assertThat(captor.getValue().entityType()).isEqualTo(EntityType.GROUP);
  }

  @Test
  void mapsNullRoleKeyToZero() {
    final var entity = new RoleEntity(null, "viewer", "Viewer", null);
    when(authenticatedRoleServices.getRole("viewer")).thenReturn(entity);

    final var result = adapter.getById("viewer");

    assertThat(result.roleKey()).isEqualTo(0L);
  }

  @Test
  void createUnwrapsRuntimeCompletionException() {
    final var rootCause = new IllegalArgumentException("duplicate");
    when(authenticatedRoleServices.createRole(any(CreateRoleRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(rootCause));

    assertThatThrownBy(() -> adapter.create("admin", "Admin", "desc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("duplicate");
  }
}
