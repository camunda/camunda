/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.Role;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GroupRoleMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;
  private final GroupRoleMigrationHandler groupRoleMigrationHandler;

  public GroupRoleMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    groupRoleMigrationHandler =
        new GroupRoleMigrationHandler(
            managementIdentityClient,
            roleServices,
            CamundaAuthentication.none(),
            migrationProperties);
  }

  @Test
  public void shouldMigrateGroupRoles() {
    // given
    // groups
    final String longGroupName = "a".repeat(300);
    final String groupNameWithUnsupportedChars = "Group@Name#With$Special%Chars";
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(
            List.of(
                new Group("id1", longGroupName),
                new Group("id2", groupNameWithUnsupportedChars),
                new Group("id3", "Normal Group")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupRoles(any()))
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role(
                    "Role@Name#With$Special%Chars", "Description for Role with special chars")))
        .thenReturn(
            List.of(
                new Role("Role 2", "Description for Role 2"),
                new Role("Role 3", "Description for Role 3")))
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role("Role 2", "Description for Role 2")));

    when(roleServices.addMember(any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    groupRoleMigrationHandler.migrate();

    // then
    final var roleMembershipCaptor = ArgumentCaptor.forClass(RoleMemberRequest.class);
    verify(roleServices, times(6)).addMember(roleMembershipCaptor.capture());
    final var requests = roleMembershipCaptor.getAllValues();
    assertThat(requests)
        .extracting(
            RoleMemberRequest::roleId, RoleMemberRequest::entityId, RoleMemberRequest::entityType)
        .containsExactlyInAnyOrder(
            tuple("role_1", "a".repeat(256), EntityType.GROUP),
            tuple("role@name_with_special_chars", "a".repeat(256), EntityType.GROUP),
            tuple("role_2", "group@name_with_special_chars", EntityType.GROUP),
            tuple("role_3", "group@name_with_special_chars", EntityType.GROUP),
            tuple("role_1", "normal_group", EntityType.GROUP),
            tuple("role_2", "normal_group", EntityType.GROUP));
  }

  @Test
  public void shouldNotBlockTheMigrationIfTheMembershipAlreadyExists() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "group1"), new Group("id2", "group2")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupRoles(any()))
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role("Role 2", "Description for Role 2")))
        .thenReturn(
            List.of(
                new Role("Role 3", "Description for Role 3"),
                new Role("Role 4", "Description for Role 4")));
    when(roleServices.addMember(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            RoleIntent.ADD_ENTITY,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "role membership exists")))));

    // when
    groupRoleMigrationHandler.migrate();

    // then
    verify(roleServices, times(4)).addMember(any());
  }

  @Test
  public void shouldRetryWithBackpressure() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "group1"), new Group("id2", "group2")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupRoles(any()))
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role("Role 2", "Description for Role 2")))
        .thenReturn(
            List.of(
                new Role("Role 3", "Description for Role 3"),
                new Role("Role 4", "Description for Role 4")));
    when(roleServices.addMember(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    groupRoleMigrationHandler.migrate();

    // then
    verify(roleServices, times(5)).addMember(any(RoleMemberRequest.class));
  }
}
