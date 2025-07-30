/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class AuthorizationMigrationHandlerTest {

  final AuthorizationServices authorizationServices;
  final ManagementIdentityClient managementIdentityClient;
  final AuthorizationMigrationHandler migrationHandler;

  public AuthorizationMigrationHandlerTest(
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices,
      @Mock final ManagementIdentityClient managementIdentityClient) {
    this.authorizationServices = authorizationServices;
    this.managementIdentityClient = managementIdentityClient;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new AuthorizationMigrationHandler(
            CamundaAuthentication.none(),
            authorizationServices,
            managementIdentityClient,
            migrationProperties);
  }

  @Test
  public void shouldMigrateAuthorizations() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    final var users1 =
        List.of(
            new User("user1", "username1", "name1", "email1"),
            new User("user2", "username2", "name2", "email2"));
    when(managementIdentityClient.fetchUsers(any(Integer.class)))
        .thenReturn(users1)
        .thenReturn(List.of());

    when(managementIdentityClient.fetchUserAuthorizations("user1"))
        .thenReturn(
            List.of(
                new Authorization(
                    "email1",
                    "USER",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE")),
                new Authorization(
                    "email1",
                    "USER",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE"))));
    when(managementIdentityClient.fetchUserAuthorizations("user2"))
        .thenReturn(
            List.of(
                new Authorization(
                    "email2",
                    "NOT_VALID",
                    "process",
                    "not-valid",
                    Set.of("UNKNOWN", "UPDATE_PROCESS_INSTANCE", "DELETE")),
                new Authorization(
                    "email2",
                    "USER",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE"))));

    // when
    migrationHandler.migrate();

    // then
    final ArgumentCaptor<CreateAuthorizationRequest> request =
        ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(4)).createAuthorization(request.capture());
    final var requests =
        request.getAllValues().stream()
            .sorted(Comparator.comparing(CreateAuthorizationRequest::ownerId))
            .toList();
    assertThat(requests, Matchers.hasSize(4));
    assertThat(requests.getFirst().ownerId(), Matchers.is("email1"));
    assertThat(requests.getFirst().ownerType(), Matchers.is(AuthorizationOwnerType.USER));
    assertThat(requests.getFirst().resourceId(), Matchers.is("process"));
    assertThat(
        requests.getFirst().resourceType(),
        Matchers.is(AuthorizationResourceType.PROCESS_DEFINITION));
    assertThat(
        requests.getFirst().permissionTypes(),
        Matchers.containsInAnyOrder(
            PermissionType.READ_PROCESS_DEFINITION,
            PermissionType.READ_PROCESS_INSTANCE,
            PermissionType.UPDATE_PROCESS_INSTANCE,
            PermissionType.CREATE_PROCESS_INSTANCE));
    assertThat(requests.get(1).ownerId(), Matchers.is("email1"));
    assertThat(requests.get(1).ownerType(), Matchers.is(AuthorizationOwnerType.USER));
    assertThat(requests.get(1).resourceId(), Matchers.is("*"));
    assertThat(
        requests.get(1).resourceType(), Matchers.is(AuthorizationResourceType.DECISION_DEFINITION));
    assertThat(
        requests.get(1).permissionTypes(),
        Matchers.containsInAnyOrder(
            PermissionType.READ_DECISION_DEFINITION,
            PermissionType.READ_DECISION_INSTANCE,
            PermissionType.DELETE_DECISION_INSTANCE));
    assertThat(requests.get(2).ownerId(), Matchers.is("email2"));
    assertThat(requests.get(2).ownerType(), Matchers.is(AuthorizationOwnerType.USER));
    assertThat(requests.get(2).resourceId(), Matchers.is("process"));
    assertThat(requests.get(2).resourceType(), Matchers.is(AuthorizationResourceType.UNSPECIFIED));
    assertThat(requests.get(2).permissionTypes(), Matchers.empty());
    assertThat(requests.get(3).ownerId(), Matchers.is("email2"));
    assertThat(requests.get(3).ownerType(), Matchers.is(AuthorizationOwnerType.USER));
    assertThat(requests.get(3).resourceId(), Matchers.is("*"));
    assertThat(
        requests.get(3).resourceType(), Matchers.is(AuthorizationResourceType.DECISION_DEFINITION));
    assertThat(
        requests.get(3).permissionTypes(),
        Matchers.containsInAnyOrder(
            PermissionType.READ_DECISION_DEFINITION,
            PermissionType.READ_DECISION_INSTANCE,
            PermissionType.DELETE_DECISION_INSTANCE));
  }

  @Test
  public void shouldContinueMigrationWithUserAuthorizationEndpointUnavailable() {
    // given
    final var users =
        List.of(
            new User("user1", "username1", "name1", "email1"),
            new User("user2", "username2", "name2", "email2"));
    when(managementIdentityClient.fetchUsers(any(Integer.class)))
        .thenReturn(users)
        .thenReturn(List.of());
    when(managementIdentityClient.fetchUserAuthorizations(anyString()))
        .thenThrow(new NotImplementedException("Authorization endpoint unavailable"));

    // when
    migrationHandler.migrate();

    // then
    // the migration to stops after the first failed invocation of fetching user authorizations
    verify(managementIdentityClient, times(1)).fetchUserAuthorizations(any());
    verify(authorizationServices, times(0)).createAuthorization(any());
  }

  @Test
  public void shouldContinueMigrationWithConflicts() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "authorization already exists")))));
    final var users1 = List.of(new User("user1", "username1", "name1", "email1"));
    when(managementIdentityClient.fetchUsers(any(Integer.class)))
        .thenReturn(users1)
        .thenReturn(List.of());

    when(managementIdentityClient.fetchUserAuthorizations(anyString()))
        .thenReturn(
            List.of(
                new Authorization(
                    "email1",
                    "USER",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE")),
                new Authorization(
                    "email1",
                    "USER",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE"))))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(authorizationServices, times(2))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }

  @Test
  public void shouldRetryOnBackpressure() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    final var users1 = List.of(new User("user1", "username1", "name1", "email1"));
    when(managementIdentityClient.fetchUsers(any(Integer.class)))
        .thenReturn(users1)
        .thenReturn(List.of());

    when(managementIdentityClient.fetchUserAuthorizations(anyString()))
        .thenReturn(
            List.of(
                new Authorization(
                    "email1",
                    "USER",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE")),
                new Authorization(
                    "email1",
                    "USER",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE"))))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(authorizationServices, times(3))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }
}
