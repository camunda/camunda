/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.UserGroups;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.search.entities.GroupEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class UsersGroupMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final GroupServices groupServices;
  private final MappingServices mappingServices;

  private final UsersGroupMigrationHandler migrationHandler;

  public UsersGroupMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final GroupServices groupServices,
      @Mock(answer = Answers.RETURNS_SELF) final MappingServices mappingServices) {
    when(groupServices.createGroup(any(), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new GroupRecord().setGroupKey(UUID.randomUUID().getMostSignificantBits())));
    when(groupServices.assignMember(anyLong(), anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));
    when(mappingServices.createMapping(any()))
        .thenReturn(CompletableFuture.completedFuture(new MappingRecord()));

    this.managementIdentityClient = managementIdentityClient;
    this.groupServices = groupServices;
    this.mappingServices = mappingServices;
    migrationHandler =
        new UsersGroupMigrationHandler(
            managementIdentityClient,
            new ManagementIdentityTransformer(),
            groupServices,
            mappingServices);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenUserGroups();

    when(groupServices.getGroupByName(any()))
        .thenReturn(new GroupEntity(1L, "", Collections.emptySet()));
    when(mappingServices.createMapping(any()))
        .thenReturn(CompletableFuture.completedFuture(new MappingRecord()));
    when(groupServices.assignMember(anyLong(), anyLong(), any(EntityType.class)))
        .thenReturn(CompletableFuture.completedFuture(new GroupRecord()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserGroups(anyInt());
    verify(groupServices, times(4)).getGroupByName(any());
    verify(groupServices, times(4)).assignMember(anyLong(), anyLong(), any(EntityType.class));
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
  }

  @Test
  void setErrorWhenGroupCreationFailed() {
    // given
    givenUserGroups();
    when(mappingServices.createMapping(any(MappingDTO.class))).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserGroups(anyInt());
    verify(groupServices, never()).findGroupByName(any());
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  Assertions.assertThat(migrationStatusUpdateRequests)
                      .describedAs("All migrations have failed")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void setErrorWhenGroupNotFound() {
    // given
    givenUserGroups();
    when(groupServices.getGroupByName(any())).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserGroups(anyInt());
    verify(groupServices, times(2)).getGroupByName(any());
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  Assertions.assertThat(migrationStatusUpdateRequests)
                      .describedAs("All migrations have failed")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void ignoreWhenUserAlreadyAssigned() {
    // given
    givenUserGroups();
    when(groupServices.getGroupByName(anyString()))
        .thenReturn(new GroupEntity(1L, "groupName", Collections.emptySet()));
    doThrow(
            new BrokerRejectionException(
                new BrokerRejection(GroupIntent.ADD_ENTITY, -1, RejectionType.ALREADY_EXISTS, "")))
        .when(groupServices)
        .assignMember(anyLong(), anyLong(), any(EntityType.class));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserGroups(anyInt());
    verify(groupServices, times(4)).getGroupByName(any());
    verify(groupServices, times(4)).assignMember(anyLong(), anyLong(), any(EntityType.class));
    verify(mappingServices, times(2)).createMapping(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests ->
                    Assertions.assertThat(migrationStatusUpdateRequests)
                        .describedAs("All migrations have succeeded")
                        .allMatch(MigrationStatusUpdateRequest::success)));
  }

  private void givenUserGroups() {
    when(managementIdentityClient.fetchUserGroups(anyInt()))
        .thenReturn(
            List.of(
                new UserGroups(
                    "user1",
                    "userName1",
                    List.of(
                        new Group(UUID.randomUUID().toString(), "group1"),
                        new Group(UUID.randomUUID().toString(), "group3"))),
                new UserGroups(
                    "user2",
                    "userName2",
                    List.of(
                        new Group(UUID.randomUUID().toString(), "group2"),
                        new Group(UUID.randomUUID().toString(), "group4")))))
        .thenReturn(Collections.emptyList());
  }
}
