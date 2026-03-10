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
import io.camunda.search.entities.GroupEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcGroupManagementAdapterTest {

  private final GroupServices groupServices = mock(GroupServices.class);
  private final GroupServices authenticatedGroupServices = mock(GroupServices.class);
  private final CamundaAuthenticationProvider authProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CamundaAuthentication authentication = CamundaAuthentication.none();

  private OcGroupManagementAdapter adapter;

  @BeforeEach
  void setUp() {
    when(authProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(groupServices.withAuthentication(authentication)).thenReturn(authenticatedGroupServices);
    adapter = new OcGroupManagementAdapter(groupServices, authProvider);
  }

  @Test
  void getByIdDelegatesToGroupServicesGetGroup() {
    final var entity = new GroupEntity(5L, "dev-team", "Dev Team", "Development");
    when(authenticatedGroupServices.getGroup("dev-team")).thenReturn(entity);

    final var result = adapter.getById("dev-team");

    assertThat(result.groupKey()).isEqualTo(5L);
    assertThat(result.groupId()).isEqualTo("dev-team");
    assertThat(result.name()).isEqualTo("Dev Team");
    assertThat(result.description()).isEqualTo("Development");
  }

  @Test
  void createDelegatesToGroupServicesCreateGroup() {
    when(authenticatedGroupServices.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var result = adapter.create("ops", "Operations", "Ops team");

    assertThat(result.groupId()).isEqualTo("ops");
    assertThat(result.name()).isEqualTo("Operations");

    final var captor = ArgumentCaptor.forClass(GroupDTO.class);
    verify(authenticatedGroupServices).createGroup(captor.capture());
    assertThat(captor.getValue().groupId()).isEqualTo("ops");
  }

  @Test
  void deleteDelegatesToGroupServicesDeleteGroup() {
    when(authenticatedGroupServices.deleteGroup("ops"))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.delete("ops");

    verify(authenticatedGroupServices).deleteGroup("ops");
  }

  @Test
  void addMemberDelegatesToGroupServicesAssignMember() {
    when(authenticatedGroupServices.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.addMember("dev-team", "alice", MemberType.USER);

    final var captor = ArgumentCaptor.forClass(GroupMemberDTO.class);
    verify(authenticatedGroupServices).assignMember(captor.capture());
    assertThat(captor.getValue().groupId()).isEqualTo("dev-team");
    assertThat(captor.getValue().memberId()).isEqualTo("alice");
    assertThat(captor.getValue().memberType()).isEqualTo(EntityType.USER);
  }

  @Test
  void mapsNullGroupKeyToZero() {
    final var entity = new GroupEntity(null, "test", "Test", null);
    when(authenticatedGroupServices.getGroup("test")).thenReturn(entity);

    final var result = adapter.getById("test");

    assertThat(result.groupKey()).isEqualTo(0L);
  }

  @Test
  void createUnwrapsRuntimeCompletionException() {
    final var rootCause = new IllegalArgumentException("duplicate");
    when(authenticatedGroupServices.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.failedFuture(rootCause));

    assertThatThrownBy(() -> adapter.create("ops", "Ops", "desc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("duplicate");
  }
}
