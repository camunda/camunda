/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.Role;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
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
  private final GroupServices groupService;
  private final AuthorizationServices authorizationServices;

  private final GroupMigrationHandler migrationHandler;

  public GroupMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final GroupServices groupService,
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupService = groupService;
    this.authorizationServices = authorizationServices;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new GroupMigrationHandler(
            managementIdentityClient,
            groupService,
            authorizationServices,
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
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    // users
    when(managementIdentityClient.fetchGroupUsers(any()))
        .thenReturn(List.of(new User("id", "username1", "name", "email")))
        .thenReturn(List.of(new User("id", "username2", "name", "email")))
        .thenReturn(List.of(new User("id", "username3", "name", "email")));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    // roles
    when(managementIdentityClient.fetchGroupRoles(any()))
        .thenReturn(List.of())
        .thenReturn(List.of())
        .thenReturn(
            List.of(
                new Role(
                    "Role@Name#With$Special%Chars", "Description for Role with special chars")));
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
    final var groupCapture = ArgumentCaptor.forClass(GroupDTO.class);
    verify(groupService, times(3)).createGroup(groupCapture.capture());
    final var capturedGroups = groupCapture.getAllValues();
    assertThat(capturedGroups).hasSize(3);
    assertThat(capturedGroups.getFirst().groupId())
        .isEqualTo(longGroupName.toLowerCase().substring(0, 256));
    assertThat(capturedGroups.get(1).groupId()).doesNotContain("#", "$", "%").contains("_");
    assertThat(capturedGroups.getLast().groupId()).isEqualTo("normal_group");

    final var memberCapture = ArgumentCaptor.forClass(GroupMemberDTO.class);
    verify(groupService, times(4)).assignMember(memberCapture.capture());
    final var capturedMembers = memberCapture.getAllValues();
    assertThat(capturedMembers).hasSize(4);
    assertThat(capturedMembers.getFirst().groupId())
        .isEqualTo(longGroupName.toLowerCase().substring(0, 256));
    assertThat(capturedMembers.getFirst().memberType()).isEqualTo(EntityType.USER);
    assertThat(capturedMembers.getFirst().memberId()).isEqualTo("username1");
    assertThat(capturedMembers.get(1).groupId()).doesNotContain("#", "$", "%").contains("_");
    assertThat(capturedMembers.get(1).memberType()).isEqualTo(EntityType.USER);
    assertThat(capturedMembers.get(1).memberId()).isEqualTo("username2");
    assertThat(capturedMembers.get(2).groupId()).isEqualTo("normal_group");
    assertThat(capturedMembers.get(2).memberType()).isEqualTo(EntityType.USER);
    assertThat(capturedMembers.get(2).memberId()).isEqualTo("username3");

    assertThat(capturedMembers.getLast().groupId()).isEqualTo("normal_group");
    assertThat(capturedMembers.getLast().memberId()).isEqualTo("role@name_with_special_chars");
    assertThat(capturedMembers.getLast().memberType()).isEqualTo(EntityType.ROLE);

    final ArgumentCaptor<CreateAuthorizationRequest> request =
        ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(3)).createAuthorization(request.capture());
    final List<CreateAuthorizationRequest> requests = request.getAllValues();
    MatcherAssert.assertThat(requests, Matchers.hasSize(3));
    MatcherAssert.assertThat(requests.getFirst().ownerId(), Matchers.is("normal_group"));
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
    MatcherAssert.assertThat(requests.get(1).ownerId(), Matchers.is("normal_group"));
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
    MatcherAssert.assertThat(requests.get(2).ownerId(), Matchers.is("normal_group"));
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
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(managementIdentityClient.fetchGroupUsers(any()))
        .thenReturn(List.of(new User("id", "username1", "name", "email")));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(managementIdentityClient.fetchGroupRoles(any())).thenReturn(List.of());
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
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "group already exists")))));
    when(managementIdentityClient.fetchGroupUsers(any()))
        .thenReturn(List.of(new User("id", "username1", "name", "email")));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.ADD_ENTITY,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "group membership already exists")))));
    when(managementIdentityClient.fetchGroupRoles(any())).thenReturn(List.of());
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
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));

    // when
    migrationHandler.migrate();
    // then
    verify(authorizationServices, times(1))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnGroupCreation() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(
            List.of(
                new Group("id1", "groupName"),
                new Group("id2", "groupName"),
                new Group("id3", "groupName")))
        .thenReturn(List.of());
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    migrationHandler.migrate();

    // then
    verify(groupService, times(4)).createGroup(any(GroupDTO.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnGroupUserAssignation() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(
            List.of(
                new Group("id1", "groupName"),
                new Group("id2", "groupName"),
                new Group("id3", "groupName")))
        .thenReturn(List.of());
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    when(managementIdentityClient.fetchGroupUsers(any()))
        .thenReturn(List.of(new User("id", "username1", "name", "email")))
        .thenReturn(List.of(new User("id", "username2", "name", "email")))
        .thenReturn(List.of(new User("id", "username3", "name", "email")));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    migrationHandler.migrate();

    // then
    verify(groupService, times(3)).createGroup(any(GroupDTO.class));
    verify(groupService, times(4)).assignMember(any(GroupMemberDTO.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnGroupRoleAssignation() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(
            List.of(
                new Group("id1", "groupName"),
                new Group("id2", "groupName"),
                new Group("id3", "groupName")))
        .thenReturn(List.of());
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    when(managementIdentityClient.fetchGroupRoles(any()))
        .thenReturn(List.of(new Role("RoleName1", "Role description")))
        .thenReturn(List.of(new Role("RoleName2", "Role description")))
        .thenReturn(List.of(new Role("RoleName3", "Role description")));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    migrationHandler.migrate();

    // then
    verify(groupService, times(3)).createGroup(any(GroupDTO.class));
    verify(groupService, times(4)).assignMember(any(GroupMemberDTO.class));
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
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

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
    verify(groupService, times(3)).createGroup(any(GroupDTO.class));
    verify(authorizationServices, times(4))
        .createAuthorization(any(CreateAuthorizationRequest.class));
  }
}
