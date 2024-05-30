/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.identity.user.Group;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class GroupServiceTest {

  @Autowired private GroupService groupService;

  @Test
  void uniqueGroupCreateUserCreated() {
    final var groupName = "gr" + UUID.randomUUID();

    final var group = groupService.createGroup(new Group(groupName));
    assertNotNull(group.id());

    final var existingGroup = groupService.findGroupByName(groupName);
    assertNotNull(existingGroup);
  }

  @Test
  void duplicateNameCreateGroupException() {
    final var groupName = "gr" + UUID.randomUUID();
    groupService.createGroup(new Group(groupName));

    assertThrows(RuntimeException.class, () -> groupService.createGroup(new Group(groupName)));
  }

  @Test
  void existingGroupDeleteGroupDeleted() {
    final var groupName = "gr" + UUID.randomUUID();
    final var group = groupService.createGroup(new Group(groupName));

    groupService.deleteGroup(group);

    assertThrows(RuntimeException.class, () -> groupService.findGroupByName(groupName));
  }

  @Test
  void existingGroupDeleteGroupByIdDeleted() {
    final var groupName = "gr" + UUID.randomUUID();
    final var groupId = groupService.createGroup(new Group(groupName)).id();

    groupService.deleteGroupById(groupId);

    assertThrows(RuntimeException.class, () -> groupService.findGroupById(groupId));
  }

  @Test
  void nonExistingGroupDeleteGroupException() {
    final var groupName = "gr" + UUID.randomUUID();
    final int randId = new Random().nextInt();

    assertThrows(RuntimeException.class, () -> groupService.deleteGroup(new Group(groupName)));
    assertThrows(RuntimeException.class, () -> groupService.deleteGroupById(randId));
  }

  @Test
  void nonExistingGroupFindGroupByNameThrowsException() {
    final var groupName = "gr" + UUID.randomUUID();

    assertThrows(RuntimeException.class, () -> groupService.findGroupByName(groupName));
  }

  @Test
  void nonExistingGroupFindGroupByIdThrowsException() {
    final int randId = new Random().nextInt();

    assertThrows(RuntimeException.class, () -> groupService.findGroupById(randId));
  }

  @Test
  void findAllGroupsReturnsAllGroups() {

    groupService.createGroup(new Group("g" + UUID.randomUUID()));
    groupService.createGroup(new Group("g" + UUID.randomUUID()));

    final var groups = groupService.findAllGroups();
    assertEquals(2, groups.size());
  }

  @Test
  void nonExistingGroupRenameGroupThrowsException() {
    final var groupName = "gr" + UUID.randomUUID();

    assertThrows(
        RuntimeException.class, () -> groupService.renameGroup(groupName, new Group(groupName)));
    assertThrows(
        RuntimeException.class, () -> groupService.renameGroupById(999, new Group(999, groupName)));
  }

  @Test
  void existingGroupRenameGroupUpdated() {
    final var groupName = "gr" + UUID.randomUUID();
    final var newGroupName = "newGr" + UUID.randomUUID();
    groupService.createGroup(new Group(groupName));

    groupService.renameGroup(groupName, new Group(newGroupName));

    final var group = groupService.findGroupByName(newGroupName);
    assertNotNull(group);
  }

  @Test
  void existingGroupRenameGroupByIdUpdated() {
    final var groupName = "gr" + UUID.randomUUID();
    final var newGroupName = "newGr" + UUID.randomUUID();
    final Integer id = groupService.createGroup(new Group(groupName)).id();

    groupService.renameGroupById(id, new Group(1, newGroupName));

    final var group = groupService.findGroupById(id);
    assertNotNull(group);
    assertEquals(newGroupName, group.name());
  }
}
