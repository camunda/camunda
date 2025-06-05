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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
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
  private final ManagementIdentityClient managementIdentityClient;

  private final GroupServices groupService;

  private final GroupMigrationHandler migrationHandler;

  public GroupMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final GroupServices groupService) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupService = groupService;
    migrationHandler =
        new GroupMigrationHandler(
            Authentication.none(),
            managementIdentityClient,
            groupService);
  }

  @Test
  void stopWhenIdentityEndpointNotFound() {
    when(managementIdentityClient.fetchGroups(anyInt())).thenThrow(new NotImplementedException());

    // when
    assertThrows(NotImplementedException.class, migrationHandler::migrate);

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
                new BrokerRejectionException(
                    new BrokerRejection(
                        GroupIntent.CREATE,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "group already exists"))));
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());

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

    // when
    migrationHandler.migrate();

    // then
    final var groupResult = ArgumentCaptor.forClass(GroupDTO.class);
    verify(groupService).createGroup(groupResult.capture());
    assertThat(groupResult.getValue().groupId())
        .describedAs("Group ID should fallback to the original ID if name is empty")
        .isEqualTo("id1");
  }
}
