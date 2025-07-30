/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.dto.ClientType.CONFIDENTIAL;
import static io.camunda.migration.identity.dto.ClientType.M2M;
import static io.camunda.migration.identity.dto.ClientType.PUBLIC;
import static io.camunda.migration.identity.dto.ClientType.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.Permission;
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
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new ClientMigrationHandler(
            CamundaAuthentication.none(),
            managementIdentityClient,
            authorizationServices,
            migrationProperties);
  }

  @Test
  public void shouldMigrateClients() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    when(managementIdentityClient.fetchClients())
        .thenReturn(
            List.of(
                new Client("client1", "ClientOne", CONFIDENTIAL),
                new Client("client2", "ClientTwo", M2M)));
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
                AuthorizationResourceType.BATCH,
                Set.of(PermissionType.CREATE, PermissionType.READ, PermissionType.UPDATE)),
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
                AuthorizationResourceType.BATCH,
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
  public void shouldSkipUnsupportedClients() {
    // given
    final String validClientId = "client3";
    when(managementIdentityClient.fetchClients())
        .thenReturn(
            List.of(
                new Client("client1", "ClientTwo", PUBLIC),
                new Client("client2", "ClientTwo", UNKNOWN),
                new Client(validClientId, "ClientOne", CONFIDENTIAL)));
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    when(managementIdentityClient.fetchClientPermissions(anyString()))
        .thenReturn(List.of(new Permission("write:*", "zeebe-api")));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(1)).fetchClientPermissions(validClientId);
    // zeebe write results in 6 authorizations
    verify(authorizationServices, times(6)).createAuthorization(any());
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
            List.of(
                new Client("client1", "ClientOne", CONFIDENTIAL),
                new Client("client2", "ClientTwo", M2M)));
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
    when(managementIdentityClient.fetchClients())
        .thenReturn(
            List.of(
                new Client("client1", "ClientOne", CONFIDENTIAL),
                new Client("client2", "ClientTwo", M2M)));
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
    verify(authorizationServices, times(17))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }
}
