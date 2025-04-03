/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.dto.Role.Permission;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled("https://github.com/camunda/camunda/issues/26973")
@ExtendWith(MockitoExtension.class)
public class RoleMigrationHandlerTest {
  private final ManagementIdentityClient managementIdentityClient;

  private final RoleServices roleServices;

  private final AuthorizationServices authorizationServices;

  private final ManagementIdentityTransformer managementIdentityTransformer =
      new ManagementIdentityTransformer();

  private final RoleMigrationHandler migrationHandler;

  //  @Captor private ArgumentCaptor<PatchAuthorizationRequest> patchAuthorizationRequestCaptor;

  public RoleMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices,
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices;
    this.authorizationServices = authorizationServices;
    migrationHandler =
        new RoleMigrationHandler(
            roleServices,
            authorizationServices,
            Authentication.none(),
            managementIdentityClient,
            managementIdentityTransformer);
    //    when(this.roleServices.createRole(anyString()))
    //        .thenReturn(CompletableFuture.completedFuture(new RoleRecord().setRoleKey(1L)))
    //        .thenReturn(CompletableFuture.completedFuture(new RoleRecord().setRoleKey(2L)));
    //    when(authorizationServices.patchAuthorization(any()))
    //        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
  }

  @Test
  void stopWhenIdentityEndpointNotFound() {
    when(managementIdentityClient.fetchRoles(anyInt())).thenThrow(new NotImplementedException());

    // when
    assertThrows(NotImplementedException.class, migrationHandler::migrate);

    // then
    verify(managementIdentityClient).fetchRoles(anyInt());
    verifyNoMoreInteractions(managementIdentityClient);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenRoles();

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchRoles(anyInt());
    verify(roleServices, times(2)).createRole(any());
    // TODO: this part needs to be revisited
    //    verify(authorizationServices, times(8))
    //        .patchAuthorization(patchAuthorizationRequestCaptor.capture());
    //    final var authorizations = patchAuthorizationRequestCaptor.getAllValues();
    //    final Map<PermissionType, Set<String>> defaultPermissionMap =
    //        Arrays.stream(PermissionType.values())
    //            .collect(
    //                Collectors.toMap(permissionType -> permissionType, permissionType ->
    // Set.of("*")));
    //    Arrays.asList(
    //            AuthorizationResourceType.PROCESS_DEFINITION,
    //            AuthorizationResourceType.DECISION_DEFINITION,
    //            AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION)
    //        .forEach(
    //            resourceType ->
    //                assertThat(authorizations)
    //                    .contains(
    //                        new PatchAuthorizationRequest(
    //                            1, PermissionAction.ADD, resourceType, defaultPermissionMap)));
    //    assertThat(authorizations)
    //        .contains(
    //            new PatchAuthorizationRequest(
    //                1,
    //                PermissionAction.ADD,
    //                AuthorizationResourceType.APPLICATION,
    //                Map.of(PermissionType.ACCESS, Set.of("operate"))));
    //    assertThat(authorizations)
    //        .contains(
    //            new PatchAuthorizationRequest(
    //                2,
    //                PermissionAction.ADD,
    //                AuthorizationResourceType.APPLICATION,
    //                Map.of(PermissionType.ACCESS, Set.of("tasklist", "operate"))));
  }

  @Test
  void setErrorWhenRoleAlreadyExists() {
    // given

    when(roleServices.createRole(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new BrokerRejectionException(
                    new BrokerRejection(
                        RoleIntent.CREATE,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "role already exists"))));
    givenRoles();

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchRoles(anyInt());
    verify(roleServices, times(2)).createRole(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("No migrations has succeeded")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void setErrorWhenRoleCreationHasError() {
    // given
    when(roleServices.createRole(any())).thenThrow(new RuntimeException());
    givenRoles();

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                statusUpdateRequests -> {
                  assertThat(statusUpdateRequests)
                      .describedAs("All migrations have failed")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
    verify(managementIdentityClient, times(2)).fetchRoles(anyInt());
    verify(roleServices, times(2)).createRole(any());
  }

  private void givenRoles() {
    when(managementIdentityClient.fetchRoles(anyInt()))
        .thenReturn(
            List.of(
                new Role(
                    "r1",
                    "d1",
                    List.of(
                        new Permission("write", "d1", "operate", "aud1"),
                        new Permission("write", "d2", "operate", "aud2"))),
                new Role(
                    "r2",
                    "d2",
                    List.of(
                        new Permission("read", "d1", "operate", "aud1"),
                        new Permission("read", "d2", "tasklist", "aud1")))))
        .thenReturn(List.of());
  }
}
