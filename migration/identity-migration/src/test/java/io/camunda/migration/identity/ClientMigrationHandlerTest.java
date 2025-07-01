/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.console.ConsoleClient.Client;
import io.camunda.migration.identity.console.ConsoleClient.Members;
import io.camunda.migration.identity.console.ConsoleClient.Permission;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ClientMigrationHandlerTest {

  final ConsoleClient consoleClient;
  final ClientMigrationHandler migrationHandler;
  private final AuthorizationServices authorizationServices;

  public ClientMigrationHandlerTest(
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices,
      @Mock final ConsoleClient consoleClient) {
    this.authorizationServices = authorizationServices;
    this.consoleClient = consoleClient;
    migrationHandler =
        new ClientMigrationHandler(
            consoleClient, authorizationServices, CamundaAuthentication.none());
  }

  @Test
  public void shouldMigrateClients() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    final var members =
        new Members(
            List.of(),
            List.of(
                new Client("client", "client-id", List.of(Permission.ZEEBE, Permission.OPERATE)),
                new Client("tasklist-client", "tasklist-client-id", List.of(Permission.TASKLIST))));
    when(consoleClient.fetchMembers()).thenReturn(members);

    // when
    migrationHandler.migrate();

    // then
    final ArgumentCaptor<CreateAuthorizationRequest> request =
        ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(9)).createAuthorization(request.capture());
    final var requests = request.getAllValues();
    assertThat(requests)
        .extracting(
            CreateAuthorizationRequest::ownerId,
            CreateAuthorizationRequest::ownerType,
            CreateAuthorizationRequest::resourceType,
            CreateAuthorizationRequest::permissionTypes)
        .contains(
            tuple(
                "client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.MESSAGE,
                Set.of(PermissionType.CREATE, PermissionType.READ)),
            tuple(
                "client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.SYSTEM,
                AuthorizationResourceType.SYSTEM.getSupportedPermissionTypes()),
            tuple(
                "client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.RESOURCE,
                Set.of(
                    PermissionType.CREATE,
                    PermissionType.DELETE_FORM,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.DELETE_DRD,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.READ)),
            tuple(
                "client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_PROCESS_INSTANCE)),
            tuple(
                "client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION,
                    PermissionType.READ_DECISION_INSTANCE)),
            tuple(
                "client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.UPDATE, PermissionType.DELETE, PermissionType.READ)),
            tuple(
                "client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.BATCH_OPERATION,
                Set.of(PermissionType.READ, PermissionType.CREATE, PermissionType.UPDATE)),
            tuple(
                "tasklist-client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.RESOURCE,
                Set.of(PermissionType.READ)),
            tuple(
                "tasklist-client-id",
                AuthorizationOwnerType.CLIENT,
                AuthorizationResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.READ_USER_TASK,
                    PermissionType.UPDATE_USER_TASK)));
  }

  @Test
  public void shouldIgnoreExistingAuthorization() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new BrokerRejectionException(
                    new BrokerRejection(
                        AuthorizationIntent.CREATE,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "authorization already exists"))));
    final var members =
        new Members(
            List.of(),
            List.of(
                new Client("client", "client-id", List.of(Permission.ZEEBE, Permission.OPERATE)),
                new Client("tasklist-client", "tasklist-client-id", List.of(Permission.TASKLIST))));
    when(consoleClient.fetchMembers()).thenReturn(members);

    // when
    migrationHandler.migrate();

    // then
    verify(authorizationServices, times(9)).createAuthorization(any());
  }
}
