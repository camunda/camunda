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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserRoleMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;
  private final UserRoleMigrationHandler userRoleMigrationHandler;

  public UserRoleMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    userRoleMigrationHandler =
        new UserRoleMigrationHandler(
            CamundaAuthentication.none(),
            managementIdentityClient,
            roleServices,
            migrationProperties);
  }

  @Test
  public void shouldMigrateUserRoles() {
    // given
    when(managementIdentityClient.fetchUsers(any(Integer.class)))
        .thenReturn(
            List.of(
                new User("user1", "username1", "name", "user1@email.com"),
                new User("user2", "username2", "name", "user2@email.com")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchUserRoles(any()))
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role(
                    "Role@Name#With$Special%Chars", "Description for Role with special chars")))
        .thenReturn(
            List.of(
                new Role("Role 2", "Description for Role 2"),
                new Role("Role 3", "Description for Role 3")));
    when(roleServices.addMember(any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    userRoleMigrationHandler.migrate();

    // then
    final var roleMembershipCaptor = ArgumentCaptor.forClass(RoleMemberRequest.class);
    verify(roleServices, times(4)).addMember(roleMembershipCaptor.capture());
    final var requests = roleMembershipCaptor.getAllValues();
    assertThat(requests)
        .extracting(RoleMemberRequest::roleId, RoleMemberRequest::entityId)
        .containsExactlyInAnyOrder(
            tuple("role_1", "user1@email.com"),
            tuple("role@name_with_special_chars", "user1@email.com"),
            tuple("role_2", "user2@email.com"),
            tuple("role_3", "user2@email.com"));
  }

  @Test
  public void shouldNotBlockTheMigrationIfTheMembershipAlreadyExists() {
    // given
    when(managementIdentityClient.fetchUsers(any(Integer.class)))
        .thenReturn(
            List.of(
                new User("user1", "username1", "name", "email@email.com"),
                new User("user2", "username2", "name", "email@email.com")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchUserRoles(any()))
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role(
                    "Role@Name#With$Special%Chars", "Description for Role with special chars")))
        .thenReturn(
            List.of(
                new Role("Role 2", "Description for Role 2"),
                new Role("Role 3", "Description for Role 3")));
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
    userRoleMigrationHandler.migrate();

    // then
    verify(roleServices, times(4)).addMember(any());
  }

  @Test
  public void shouldRetryWithBackpressure() {
    // given
    when(managementIdentityClient.fetchUsers(any(Integer.class)))
        .thenReturn(
            List.of(
                new User("user1", "username1", "name", "user1@email.com"),
                new User("user2", "username2", "name", "user2@email.com")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchUserRoles(any()))
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role(
                    "Role@Name#With$Special%Chars", "Description for Role with special chars")))
        .thenReturn(
            List.of(
                new Role("Role 2", "Description for Role 2"),
                new Role("Role 3", "Description for Role 3")));
    when(roleServices.addMember(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    userRoleMigrationHandler.migrate();

    // then
    verify(roleServices, times(5)).addMember(any(RoleMemberRequest.class));
  }
}
