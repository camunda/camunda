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

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Permission;
import io.camunda.migration.identity.dto.Role;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class KeycloakRoleMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;
  private final AuthorizationServices authorizationServices;

  private final RoleMigrationHandler roleMigrationHandler;

  public KeycloakRoleMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices,
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices;
    this.authorizationServices = authorizationServices;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    roleMigrationHandler =
        new RoleMigrationHandler(
            CamundaAuthentication.none(),
            managementIdentityClient,
            roleServices,
            authorizationServices,
            migrationProperties);
  }

  @Test
  public void shouldMigrateRolesAndAuthorizations() {
    // given
    when(managementIdentityClient.fetchRoles())
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role(
                    "Role@Name#With$Special%Chars", "Description for Role with special chars")));
    when(roleServices.createRole(any()))
        .thenReturn(CompletableFuture.completedFuture(new RoleRecord()));
    when(managementIdentityClient.fetchPermissions(any()))
        .thenReturn(
            List.of(
                new Permission("read", "camunda-identity-resource-server"),
                new Permission("read:users", "camunda-identity-resource-server"),
                new Permission("write", "camunda-identity-resource-server"),
                new Permission("read:*", "operate-api"),
                new Permission("write:*", "operate-api")))
        .thenReturn(
            List.of(
                new Permission("read:*", "tasklist-api"),
                new Permission("write:*", "tasklist-api"),
                new Permission("write:*", "zeebe-api")));
    when(authorizationServices.createAuthorization(any()))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));

    // when
    roleMigrationHandler.migrate();

    // then
    final var roleCaptor = ArgumentCaptor.forClass(CreateRoleRequest.class);
    verify(roleServices, Mockito.times(2)).createRole(roleCaptor.capture());
    final var roleRequests = roleCaptor.getAllValues();
    assertThat(roleRequests).hasSize(2);
    assertThat(roleRequests.getFirst().roleId()).isEqualTo("role_1");
    assertThat(roleRequests.getFirst().name()).isEqualTo("Role 1");
    assertThat(roleRequests.getFirst().description()).isEqualTo("Description for Role 1");
    assertThat(roleRequests.getLast().roleId()).isEqualTo("role@name_with_special_chars");
    assertThat(roleRequests.getLast().name()).isEqualTo("Role@Name#With$Special%Chars");
    assertThat(roleRequests.getLast().description())
        .isEqualTo("Description for Role with special chars");

    final var authorizationCaptor = ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, Mockito.times(20))
        .createAuthorization(authorizationCaptor.capture());
    final var authorizationRequests = authorizationCaptor.getAllValues();
    assertThat(authorizationRequests).hasSize(20);
    assertThat(authorizationRequests)
        .extracting(
            CreateAuthorizationRequest::ownerId,
            CreateAuthorizationRequest::ownerType,
            CreateAuthorizationRequest::resourceType,
            CreateAuthorizationRequest::permissionTypes)
        .containsExactlyInAnyOrder(
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.USER,
                Set.of(PermissionType.READ)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.BATCH,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.ROLE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.UPDATE,
                    PermissionType.DELETE,
                    PermissionType.CREATE)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.RESOURCE,
                Set.of(PermissionType.READ)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.MESSAGE,
                Set.of(PermissionType.READ)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.GROUP,
                Set.of(
                    PermissionType.READ,
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.AUTHORIZATION,
                Set.of(
                    PermissionType.READ,
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)),
            tuple(
                "role_1",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.TENANT,
                Set.of(
                    PermissionType.READ,
                    PermissionType.CREATE,
                    PermissionType.UPDATE,
                    PermissionType.DELETE)),
            tuple(
                "role@name_with_special_chars",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.SYSTEM,
                Set.of(PermissionType.READ, PermissionType.UPDATE)),
            tuple(
                "role@name_with_special_chars",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                "role@name_with_special_chars",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE)),
            tuple(
                "role@name_with_special_chars",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.UPDATE, PermissionType.DELETE)),
            tuple(
                "role@name_with_special_chars",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.MESSAGE,
                Set.of(PermissionType.CREATE)),
            tuple(
                "role@name_with_special_chars",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.RESOURCE,
                Set.of(
                    PermissionType.READ,
                    PermissionType.CREATE,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_RESOURCE)),
            tuple(
                "role@name_with_special_chars",
                AuthorizationOwnerType.ROLE,
                AuthorizationResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE)));
  }

  @Test
  public void shouldNotBlockTheMigrationIfRolesOrAuthorizationsAlreadyExists() {
    // given
    when(managementIdentityClient.fetchRoles())
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role(
                    "Role@Name#With$Special%Chars", "Description for Role with special chars")));
    when(roleServices.createRole(any(CreateRoleRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            AuthorizationIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "role already exists")))));
    when(managementIdentityClient.fetchPermissions(any()))
        .thenReturn(
            List.of(
                new Permission("read", "camunda-identity-resource-server"),
                new Permission("read:users", "camunda-identity-resource-server"),
                new Permission("write", "camunda-identity-resource-server"),
                new Permission("read:*", "operate-api"),
                new Permission("write:*", "operate-api")))
        .thenReturn(
            List.of(
                new Permission("read:*", "tasklist-api"),
                new Permission("write:*", "tasklist-api"),
                new Permission("write:*", "zeebe-api")));
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            AuthorizationIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "authorization already exists")))));

    // when
    roleMigrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchPermissions(any());
    verify(authorizationServices, times(20)).createAuthorization(any());
  }

  @Test
  public void shouldRetryWithBackpressureOnRoleCreation() {
    // given
    when(managementIdentityClient.fetchRoles())
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role("Role 2", "Description for Role 2")));
    when(roleServices.createRole(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(new RoleRecord()));

    // when
    roleMigrationHandler.migrate();

    // then
    verify(roleServices, Mockito.times(3)).createRole(any(CreateRoleRequest.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnRoleAuthorizationCreation() {
    // given
    when(managementIdentityClient.fetchRoles())
        .thenReturn(
            List.of(
                new Role("Role 1", "Description for Role 1"),
                new Role("Role 2", "Description for Role 2")));
    when(roleServices.createRole(any()))
        .thenReturn(CompletableFuture.completedFuture(new RoleRecord()));

    when(managementIdentityClient.fetchPermissions(any()))
        .thenReturn(
            List.of(
                new Permission("read", "camunda-identity-resource-server"),
                new Permission("read:users", "camunda-identity-resource-server"),
                new Permission("write", "camunda-identity-resource-server"),
                new Permission("read:*", "operate-api"),
                new Permission("write:*", "operate-api")))
        .thenReturn(
            List.of(
                new Permission("read:*", "tasklist-api"),
                new Permission("write:*", "tasklist-api"),
                new Permission("write:*", "zeebe-api")));
    when(authorizationServices.createAuthorization(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));

    // when
    roleMigrationHandler.migrate();

    // then
    verify(roleServices, Mockito.times(2)).createRole(any(CreateRoleRequest.class));
    verify(authorizationServices, Mockito.times(21))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }
}
