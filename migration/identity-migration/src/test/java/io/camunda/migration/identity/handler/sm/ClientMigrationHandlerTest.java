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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.Permission;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
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

  private final ManagementIdentityClient managementIdentityClient;
  private final ClientMigrationHandler migrationHandler;
  private final AuthorizationServices authorizationServices;

  public ClientMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.authorizationServices = authorizationServices;
    migrationHandler =
        new ClientMigrationHandler(
            CamundaAuthentication.none(), managementIdentityClient, authorizationServices);
  }

  @Test
  public void shouldMigrateClients() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    when(managementIdentityClient.fetchClients())
        .thenReturn(
            List.of(new Client("client1", "ClientOne"), new Client("client2", "ClientTwo")));
    when(managementIdentityClient.fetchClientPermissions(anyString()))
        .thenReturn(
            List.of(
                new Permission("write:*", "zeebe-api"), new Permission("write:*", "operate-api")))
        .thenReturn(
            List.of(
                new Permission("read:*", "operate-api"),
                new Permission("write:*", "tasklist-api")));

    // when
    migrationHandler.migrate();

    // then
    final ArgumentCaptor<CreateAuthorizationRequest> request =
        ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(16)).createAuthorization(request.capture());
    final var requests = request.getAllValues();
    assertThat(requests)
        .extracting(
            CreateAuthorizationRequest::ownerId,
            CreateAuthorizationRequest::ownerType,
            CreateAuthorizationRequest::resourceId,
            CreateAuthorizationRequest::resourceType,
            CreateAuthorizationRequest::permissionTypes)
        .contains(
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.MESSAGE,
                Set.of(PermissionType.CREATE, PermissionType.READ)),
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.RESOURCE,
                Set.of(
                    PermissionType.DELETE_FORM,
                    PermissionType.CREATE,
                    PermissionType.DELETE_RESOURCE,
                    PermissionType.DELETE_PROCESS,
                    PermissionType.READ,
                    PermissionType.DELETE_DRD)),
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.SYSTEM,
                Set.of(PermissionType.UPDATE, PermissionType.READ)),
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "operate",
                AuthorizationResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.BATCH_OPERATION,
                Set.of(PermissionType.CREATE, PermissionType.READ)),
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ, PermissionType.UPDATE, PermissionType.DELETE)),
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.DELETE_DECISION_INSTANCE,
                    PermissionType.CREATE_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION)),
            tuple(
                "ClientOne",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.DELETE_PROCESS_INSTANCE,
                    PermissionType.UPDATE_PROCESS_INSTANCE,
                    PermissionType.CREATE_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_DEFINITION)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.DECISION_DEFINITION,
                Set.of(
                    PermissionType.READ_DECISION_INSTANCE,
                    PermissionType.READ_DECISION_DEFINITION)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.RESOURCE,
                Set.of(PermissionType.READ)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.PROCESS_DEFINITION,
                Set.of(
                    PermissionType.UPDATE_USER_TASK,
                    PermissionType.READ_USER_TASK,
                    PermissionType.READ_PROCESS_INSTANCE,
                    PermissionType.READ_PROCESS_DEFINITION)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION,
                Set.of(PermissionType.READ)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "operate",
                AuthorizationResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.BATCH_OPERATION,
                Set.of(PermissionType.READ)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "*",
                AuthorizationResourceType.MESSAGE,
                Set.of(PermissionType.READ)),
            tuple(
                "ClientTwo",
                AuthorizationOwnerType.CLIENT,
                "tasklist",
                AuthorizationResourceType.APPLICATION,
                Set.of(PermissionType.ACCESS)));
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
    when(managementIdentityClient.fetchClients())
        .thenReturn(
            List.of(new Client("client1", "ClientOne"), new Client("client2", "ClientTwo")));
    when(managementIdentityClient.fetchClientPermissions(anyString()))
        .thenReturn(
            List.of(
                new Permission("write:*", "zeebe-api"), new Permission("write:*", "operate-api")))
        .thenReturn(
            List.of(
                new Permission("read:*", "operate-api"),
                new Permission("write:*", "tasklist-api")));

    // when
    migrationHandler.migrate();

    //
    verify(authorizationServices, times(16))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }
}
