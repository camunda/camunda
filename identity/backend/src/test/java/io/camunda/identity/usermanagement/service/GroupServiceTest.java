/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.usermanagement.CamundaGroup;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringBootTest
public class GroupServiceTest {

  @Autowired private GroupService groupService;

  @Test
  void uniqueGroupCreateGroupCreated() {
    final var groupName = "gr" + UUID.randomUUID();

    final var group = groupService.createGroup(new CamundaGroup(groupName));
    assertNotNull(group.id());

    final var existingGroup = groupService.findGroupByName(groupName);
    assertNotNull(existingGroup);
  }

  @Test
  void duplicateNameCreateGroupException() {
    final var groupName = "gr" + UUID.randomUUID();
    groupService.createGroup(new CamundaGroup(groupName));

    assertThrows(
        RuntimeException.class, () -> groupService.createGroup(new CamundaGroup(groupName)));
  }

  @Test
  void existingGroupDeleteGroupDeleted() {
    final var groupName = "gr" + UUID.randomUUID();
    final var group = groupService.createGroup(new CamundaGroup(groupName));

    assertDoesNotThrow(() -> (groupService.findGroupById(group.id())));

    groupService.deleteGroup(group);

    assertThrows(RuntimeException.class, () -> groupService.findGroupByName(groupName));
  }

  @Test
  void existingGroupDeleteGroupByIdDeleted() {
    final var groupName = "gr" + UUID.randomUUID();
    final var group = groupService.createGroup(new CamundaGroup(groupName));

    assertDoesNotThrow(() -> (groupService.findGroupById(group.id())));

    groupService.deleteGroupById(group.id());

    assertThrows(RuntimeException.class, () -> groupService.findGroupById(group.id()));
  }

  @Test
  void nonExistingGroupDeleteGroupException() {
    final var groupName = "gr" + UUID.randomUUID();
    final long randId = new Random().nextLong();

    assertThrows(
        RuntimeException.class, () -> groupService.deleteGroup(new CamundaGroup(groupName)));
    assertThrows(RuntimeException.class, () -> groupService.deleteGroupById(randId));
  }

  @Test
  void nonExistingGroupFindGroupThrowsException() {
    final var groupName = "gr" + UUID.randomUUID();
    final long randId = new Random().nextLong();

    assertThrows(RuntimeException.class, () -> groupService.findGroupByName(groupName));
    assertThrows(RuntimeException.class, () -> groupService.findGroupById(randId));
  }

  @Test
  void findAllGroupsReturnsAllGroups() {

    groupService.createGroup(new CamundaGroup("g" + UUID.randomUUID()));
    groupService.createGroup(new CamundaGroup("g" + UUID.randomUUID()));

    final var groups = groupService.findAllGroups();
    assertEquals(2, groups.size());
  }

  @Test
  void nonExistingGroupRenameGroupThrowsException() {
    final var groupName = "gr" + UUID.randomUUID();
    final long randId = new Random().nextLong();

    assertThrows(
        RuntimeException.class,
        () -> groupService.updateGroup(randId, new CamundaGroup(randId, groupName)));
  }

  @Test
  void existingGroupUpdateGroupUpdated() {
    final var groupName = "gr" + UUID.randomUUID();
    final var newGroupName = "newGr" + UUID.randomUUID();
    final CamundaGroup createdGroup = groupService.createGroup(new CamundaGroup(groupName));

    groupService.updateGroup(createdGroup.id(), new CamundaGroup(newGroupName));

    final var group = groupService.findGroupById(createdGroup.id());
    assertNotNull(group);
  }
}
