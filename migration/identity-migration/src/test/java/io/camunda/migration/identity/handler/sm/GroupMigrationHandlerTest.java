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
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.Role;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GroupMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final AuthorizationServices authorizationServices;
  private final RoleServices roleServices;

  private final GroupMigrationHandler migrationHandler;

  public GroupMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices,
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.authorizationServices = authorizationServices;
    this.roleServices = roleServices;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new GroupMigrationHandler(
            managementIdentityClient,
            authorizationServices,
            roleServices,
            CamundaAuthentication.none(),
            migrationProperties);
  }

  @Test
  public void shouldMigrateGroups() {
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
    // authorizations
    when(managementIdentityClient.fetchGroupAuthorizations(any()))
        .thenReturn(List.of())
        .thenReturn(List.of())
        .thenReturn(
            List.of(
                new Authorization(
                    "group3",
                    "GROUP",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE")),
                new Authorization(
                    "group3",
                    "GROUP",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE")),
                new Authorization(
                    "group3",
                    "GROUP",
                    "process",
                    "not-valid",
                    Set.of("UNKNOWN", "UPDATE_PROCESS_INSTANCE", "DELETE"))));
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));

    // when
    migrationHandler.migrate();

    // then
    final ArgumentCaptor<CreateAuthorizationRequest> request =
        ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(3)).createAuthorization(request.capture());
    final List<CreateAuthorizationRequest> requests = request.getAllValues();
    MatcherAssert.assertThat(requests, Matchers.hasSize(3));
    MatcherAssert.assertThat(requests.getFirst().ownerId(), Matchers.is("Normal Group"));
    MatcherAssert.assertThat(
        requests.getFirst().ownerType(), Matchers.is(AuthorizationOwnerType.GROUP));
    MatcherAssert.assertThat(requests.getFirst().resourceId(), Matchers.is("process"));
    MatcherAssert.assertThat(
        requests.getFirst().resourceType(),
        Matchers.is(AuthorizationResourceType.PROCESS_DEFINITION));
    MatcherAssert.assertThat(
        requests.getFirst().permissionTypes(),
        Matchers.containsInAnyOrder(
            PermissionType.READ_PROCESS_DEFINITION,
            PermissionType.READ_PROCESS_INSTANCE,
            PermissionType.UPDATE_PROCESS_INSTANCE,
            PermissionType.CREATE_PROCESS_INSTANCE));
    MatcherAssert.assertThat(requests.get(1).ownerId(), Matchers.is("Normal Group"));
    MatcherAssert.assertThat(
        requests.get(1).ownerType(), Matchers.is(AuthorizationOwnerType.GROUP));
    MatcherAssert.assertThat(requests.get(1).resourceId(), Matchers.is("*"));
    MatcherAssert.assertThat(
        requests.get(1).resourceType(), Matchers.is(AuthorizationResourceType.DECISION_DEFINITION));
    MatcherAssert.assertThat(
        requests.get(1).permissionTypes(),
        Matchers.containsInAnyOrder(
            PermissionType.READ_DECISION_DEFINITION,
            PermissionType.READ_DECISION_INSTANCE,
            PermissionType.DELETE_DECISION_INSTANCE));
    MatcherAssert.assertThat(requests.get(2).ownerId(), Matchers.is("Normal Group"));
    MatcherAssert.assertThat(
        requests.get(2).ownerType(), Matchers.is(AuthorizationOwnerType.GROUP));
    MatcherAssert.assertThat(requests.get(2).resourceId(), Matchers.is("process"));
    MatcherAssert.assertThat(
        requests.get(2).resourceType(), Matchers.is(AuthorizationResourceType.UNSPECIFIED));
    MatcherAssert.assertThat(requests.get(2).permissionTypes(), Matchers.empty());
  }

  @Test
  public void shouldContinueMigrationWithAuthorizationsEndpointUnavailable() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "Group1")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupAuthorizations(any()))
        .thenThrow(new NotImplementedException("Authorizations endpoint unavailable"));

    verify(authorizationServices, times(0))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }

  @Test
  public void shouldContinueMigrationIfConflicts() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "Group1")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupAuthorizations(any()))
        .thenReturn(
            List.of(
                new Authorization(
                    "group3",
                    "GROUP",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE"))));
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
    migrationHandler.migrate();
    // then
    verify(authorizationServices, times(1))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnGroupAuthorizationCreation() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(
            List.of(
                new Group("id1", "groupName"),
                new Group("id2", "groupName"),
                new Group("id3", "groupName")))
        .thenReturn(List.of());

    when(managementIdentityClient.fetchGroupAuthorizations(any()))
        .thenReturn(List.of())
        .thenReturn(List.of())
        .thenReturn(
            List.of(
                new Authorization(
                    "group3",
                    "GROUP",
                    "process",
                    "process-definition",
                    Set.of("READ", "UPDATE_PROCESS_INSTANCE", "START_PROCESS_INSTANCE")),
                new Authorization(
                    "group3",
                    "GROUP",
                    "*",
                    "decision-definition",
                    Set.of("DELETE_PROCESS_INSTANCE", "READ", "DELETE")),
                new Authorization(
                    "group3",
                    "GROUP",
                    "process",
                    "not-valid",
                    Set.of("UNKNOWN", "UPDATE_PROCESS_INSTANCE", "DELETE"))));
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));

    // when
    migrationHandler.migrate();

    // then
    verify(authorizationServices, times(4))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }

  @Test
  public void shouldMigrateGroupRoles() {
    // given
    // groups
    final String longGroupName = "a".repeat(300);
    final String groupNameWithSpecialChars = "Group@Name With$Special%Chars";
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(
            List.of(
                new Group("id1", longGroupName),
                new Group("id2", groupNameWithSpecialChars),
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
    migrationHandler.migrate();

    // then
    final var roleMembershipCaptor = ArgumentCaptor.forClass(RoleMemberRequest.class);
    verify(roleServices, times(6)).addMember(roleMembershipCaptor.capture());
    final var requests = roleMembershipCaptor.getAllValues();
    assertThat(requests)
        .extracting(
            RoleMemberRequest::roleId, RoleMemberRequest::entityId, RoleMemberRequest::entityType)
        .containsExactlyInAnyOrder(
            tuple("role_1", longGroupName, EntityType.GROUP),
            tuple("role@name_with_special_chars", longGroupName, EntityType.GROUP),
            tuple("role_2", groupNameWithSpecialChars, EntityType.GROUP),
            tuple("role_3", groupNameWithSpecialChars, EntityType.GROUP),
            tuple("role_1", "Normal Group", EntityType.GROUP),
            tuple("role_2", "Normal Group", EntityType.GROUP));
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
    migrationHandler.migrate();

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
    migrationHandler.migrate();

    // then
    verify(roleServices, times(5)).addMember(any(RoleMemberRequest.class));
  }
}
