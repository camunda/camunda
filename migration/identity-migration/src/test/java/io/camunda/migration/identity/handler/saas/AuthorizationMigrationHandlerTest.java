/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.client.ConsoleClient.Member;
import io.camunda.migration.identity.client.ConsoleClient.Role;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
  final ConsoleClient consoleClient;

  public AuthorizationMigrationHandlerTest(
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices,
      @Mock final ConsoleClient consoleClient,
      @Mock final ManagementIdentityClient managementIdentityClient) {
    this.authorizationServices = authorizationServices;
    this.managementIdentityClient = managementIdentityClient;
    this.consoleClient = consoleClient;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new AuthorizationMigrationHandler(
            CamundaAuthentication.none(),
            authorizationServices,
            consoleClient,
            managementIdentityClient,
            migrationProperties);
  }

  @Test
  public void shouldMigrateAuthorizations() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    final var members =
        new ConsoleClient.Members(
            List.of(
                new Member("user1", List.of(Role.DEVELOPER), "user1@email.com", "User One"),
                new Member(
                    "user2", List.of(Role.OPERATIONS_ENGINEER), "user2@email.com", "User Two"),
                new Member("user3", List.of(Role.IGNORED), "user3@email.com", "User Three")),
            List.of());
    when(consoleClient.fetchMembers()).thenReturn(members);

    when(managementIdentityClient.fetchAuthorizations())
        .thenReturn(
            List.of(
                new Authorization(
                    "user1",
                    "USER",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE")),
                new Authorization(
                    "user2",
                    "USER",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE")),
                new Authorization(
                    "user3",
                    "NOT_VALID",
                    "process",
                    "not-valid",
                    Set.of("UNKNOWN", "UPDATE_PROCESS_INSTANCE", "DELETE"))));

    // when
    migrationHandler.migrate();

    // then
    final ArgumentCaptor<CreateAuthorizationRequest> request =
        ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(3)).createAuthorization(request.capture());
    final List<CreateAuthorizationRequest> requests = request.getAllValues();
    assertThat(requests, Matchers.hasSize(3));
    assertThat(requests.getFirst().ownerId(), Matchers.is("user1@email.com"));
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
    assertThat(requests.get(1).ownerId(), Matchers.is("user2@email.com"));
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
    assertThat(requests.get(2).ownerId(), Matchers.is("user3@email.com"));
    assertThat(requests.get(2).ownerType(), Matchers.is(AuthorizationOwnerType.UNSPECIFIED));
    assertThat(requests.get(2).resourceId(), Matchers.is("process"));
    assertThat(requests.get(2).resourceType(), Matchers.is(AuthorizationResourceType.UNSPECIFIED));
    assertThat(requests.get(2).permissionTypes(), Matchers.empty());
  }

  @Test
  public void shouldRetryWithBackpressure() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    final var members =
        new ConsoleClient.Members(
            List.of(
                new Member("user1", List.of(Role.DEVELOPER), "user1@email.com", "User One"),
                new Member(
                    "user2", List.of(Role.OPERATIONS_ENGINEER), "user2@email.com", "User Two"),
                new Member("user3", List.of(Role.IGNORED), "user3@email.com", "User Three")),
            List.of());
    when(consoleClient.fetchMembers()).thenReturn(members);

    when(managementIdentityClient.fetchAuthorizations())
        .thenReturn(
            List.of(
                new Authorization(
                    "user1",
                    "USER",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE")),
                new Authorization(
                    "user2",
                    "USER",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE")),
                new Authorization(
                    "user3",
                    "NOT_VALID",
                    "process",
                    "not-valid",
                    Set.of("UNKNOWN", "UPDATE_PROCESS_INSTANCE", "DELETE"))));

    // when
    migrationHandler.migrate();

    // then
    verify(authorizationServices, times(4)).createAuthorization(any());
  }
}
