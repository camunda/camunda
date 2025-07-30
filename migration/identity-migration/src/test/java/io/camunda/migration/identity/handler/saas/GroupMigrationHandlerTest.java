/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.client.ConsoleClient.Member;
import io.camunda.migration.identity.client.ConsoleClient.Members;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Group;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GroupMigrationHandlerTest {
  private final ConsoleClient consoleClient;
  private final ManagementIdentityClient managementIdentityClient;

  private final GroupServices groupService;

  private final GroupMigrationHandler migrationHandler;

  public GroupMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock final ConsoleClient consoleClient,
      @Mock(answer = Answers.RETURNS_SELF) final GroupServices groupService) {
    this.consoleClient = consoleClient;
    this.managementIdentityClient = managementIdentityClient;
    this.groupService = groupService;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new GroupMigrationHandler(
            CamundaAuthentication.none(),
            consoleClient,
            managementIdentityClient,
            groupService,
            migrationProperties);
  }

  @Test
  void stopWhenIdentityEndpointNotFound() {
    when(managementIdentityClient.fetchGroups(anyInt())).thenThrow(new NotImplementedException());
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    assertThatExceptionOfType(NotImplementedException.class).isThrownBy(migrationHandler::migrate);

    // then
    verify(managementIdentityClient).fetchGroups(anyInt());
    verifyNoMoreInteractions(managementIdentityClient);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).createGroup(any(GroupDTO.class));
  }

  @Test
  void ignoreWhenGroupAlreadyExists() {
    // given

    when(groupService.createGroup(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "group already exists")))));
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).createGroup(any());
  }

  @Test
  public void shouldNormalizeGroupIDIfTooLong() {
    // given
    final String longGroupName = "a".repeat(300);
    final Group group = new Group("id1", longGroupName);
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    final var groupResult = ArgumentCaptor.forClass(GroupDTO.class);
    verify(groupService, times(1)).createGroup(groupResult.capture());
    assertThat(groupResult.getValue().groupId())
        .describedAs("Group ID should be normalized to 256 characters")
        .hasSize(256);
    assertThat(groupResult.getValue().groupId())
        .describedAs("Group ID should be lowercased")
        .isEqualTo(longGroupName.toLowerCase().substring(0, 256));
  }

  @Test
  public void shouldNormalizeGroupIDIfContainsUnsupportedCharacters() {
    // given
    final String groupNameWithUnsupportedChars = "Group@Name#With$Special%Chars";
    final Group group = new Group("id1", groupNameWithUnsupportedChars);
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    final var groupResult = ArgumentCaptor.forClass(GroupDTO.class);
    verify(groupService).createGroup(groupResult.capture());
    assertThat(groupResult.getValue().groupId())
        .describedAs("Group ID should be normalized to remove unsupported characters")
        .doesNotContain("#", "$", "%")
        .contains("_");
  }

  @Test
  public void shouldFallbackToGroupIdIfNameIsEmpty() {
    // given
    final Group group = new Group("id1", "");
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    final var groupResult = ArgumentCaptor.forClass(GroupDTO.class);
    verify(groupService).createGroup(groupResult.capture());
    assertThat(groupResult.getValue().groupId())
        .describedAs("Group ID should fallback to the original ID if name is empty")
        .isEqualTo("id1");
  }

  @Test
  public void shouldAssignUsersToGroup() {
    // given
    final var groupId = "groupId";
    final Group group = new Group(groupId, "Test Group");
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupUsers(groupId))
        .thenReturn(List.of(new User("user1", "username", "name", "email@email.com")));
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    final var groupMember = ArgumentCaptor.forClass(GroupMemberDTO.class);
    verify(groupService).assignMember(groupMember.capture());
    assertThat(groupMember.getValue().groupId())
        .describedAs("Group ID should match the migrated group ID")
        .isEqualTo("test_group");
    assertThat(groupMember.getValue().memberId())
        .describedAs("Member ID should match the user email")
        .isEqualTo("email@email.com");
    assertThat(groupMember.getValue().memberType())
        .describedAs("Entity type should be USER")
        .isEqualTo(EntityType.USER);
  }

  @Test
  public void shouldAssignUsersToGroupWhenGroupAlreadyExists() {
    // given
    final var groupId = "groupId";
    final Group group = new Group(groupId, "Test Group");
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupUsers(groupId))
        .thenReturn(List.of(new User("user1", "username", "name", "email@email.com")));
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
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    verify(groupService).assignMember(any());
  }

  @Test
  public void shouldIgnoreAlreadyAssignedUsers() {
    // given
    final var groupId = "groupId";
    final Group group = new Group(groupId, "Test Group");
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupUsers(groupId))
        .thenReturn(
            List.of(
                new User("user1", "username", "name", "email@email.com"),
                new User("user2", "username", "name", "email@email.com")));
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.ADD_ENTITY,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "member already exists")))));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).assignMember(any(GroupMemberDTO.class));
  }

  @Test
  public void shouldResolveUserEmailFromConsoleResponseWhenMissingInIdentity() {
    // given
    final var groupId = "groupId";
    final Group group = new Group(groupId, "Test Group");
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupUsers(groupId))
        // no email present in identity response
        .thenReturn(List.of(new User("user1", null, null, null)));
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(consoleClient.fetchMembers())
        .thenReturn(
            new Members(
                List.of(new Member("user1", List.of(), "user1@camunda.com", "User 1")), List.of()));

    // when
    migrationHandler.migrate();

    // then
    final var groupMember = ArgumentCaptor.forClass(GroupMemberDTO.class);
    verify(groupService).assignMember(groupMember.capture());
    assertThat(groupMember.getValue().groupId())
        .describedAs("Group ID should match the migrated group ID")
        .isEqualTo("test_group");
    assertThat(groupMember.getValue().memberId())
        .describedAs("Member ID should match the user email")
        .isEqualTo("user1@camunda.com");
    assertThat(groupMember.getValue().memberType())
        .describedAs("Entity type should be USER")
        .isEqualTo(EntityType.USER);
  }

  @Test
  public void shouldSkipMemberWhenEmailCantBeResolved() {
    // given
    final var groupId = "groupId";
    final Group group = new Group(groupId, "Test Group");
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(group))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupUsers(groupId))
        // no email present in identity response
        .thenReturn(List.of(new User("user1", null, null, null)));
    when(groupService.createGroup(any(GroupDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // no email information from console either
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    final var groupMember = ArgumentCaptor.forClass(GroupMemberDTO.class);
    verify(groupService, never()).assignMember(groupMember.capture());
  }

  @Test
  void shouldRetryWithBackpressureOnGroupCreation() {
    // given

    when(groupService.createGroup(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupUsers(anyString()))
        .thenReturn(List.of(new User("user1", "username", "name", "email@email.com")));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    verify(groupService, times(3)).createGroup(any(GroupDTO.class));
    verify(groupService, times(2)).assignMember(any(GroupMemberDTO.class));
  }

  @Test
  void shouldRetryWithBackpressureOnGroupMembershipAssignation() {
    // given

    when(groupService.createGroup(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(groupService.assignMember(any(GroupMemberDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());
    when(managementIdentityClient.fetchGroupUsers(anyString()))
        .thenReturn(List.of(new User("user1", "username", "name", "email@email.com")));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));

    // when
    migrationHandler.migrate();

    // then
    verify(groupService, times(2)).createGroup(any(GroupDTO.class));
    verify(groupService, times(3)).assignMember(any(GroupMemberDTO.class));
  }
}
