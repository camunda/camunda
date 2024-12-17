/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.GroupService;
import io.camunda.zeebe.client.api.command.ProblemException;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GroupMigrationHandlerTest {
  final ArgumentCaptor<Collection<MigrationStatusUpdateRequest>> migrationStatusCaptor =
      ArgumentCaptor.forClass(Collection.class);
  @Mock private ManagementIdentityClient managementIdentityClient;
  @Mock private GroupService groupService;
  private final ManagementIdentityTransformer managementIdentityTransformer =
      new ManagementIdentityTransformer();
  private GroupMigrationHandler migrationHandler;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    migrationHandler =
        new GroupMigrationHandler(
            managementIdentityClient, managementIdentityTransformer, groupService);
  }

  @AfterEach
  void tearDown() {}

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
    verify(groupService, times(2)).create(any());
  }

  @Test
  void ignoreWhenGroupAlreadyExists() {
    // given

    when(groupService.create(any()))
        .thenThrow(new ProblemException(0, "Failed with code 409: 'Conflict'", null));
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).create(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .allMatch(MigrationStatusUpdateRequest::success),
        "All requests should succeed");
  }

  @Test
  void setErrorWhenGroupCreationHasError() {
    // given
    when(groupService.create(any())).thenThrow(new ProblemException(0, "runtime exception!", null));
    when(managementIdentityClient.fetchGroups(anyInt()))
        .thenReturn(List.of(new Group("id1", "t1"), new Group("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .noneMatch(MigrationStatusUpdateRequest::success),
        "All requests should failed");
    verify(managementIdentityClient, times(2)).fetchGroups(anyInt());
    verify(groupService, times(2)).create(any());
  }
}
