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

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.usermanagement.CamundaGroup;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringBootTest
public class GroupServiceTest {
  @Autowired private GroupService groupService;

  @Test
  void uniqueGroupCreateUserCreated() {
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

    groupService.deleteGroup(group);

    assertThrows(RuntimeException.class, () -> groupService.findGroupByName(groupName));
  }

  @Test
  void nonExistingGroupDeleteGroupException() {
    final var groupName = "gr" + UUID.randomUUID();

    assertThrows(
        RuntimeException.class, () -> groupService.deleteGroup(new CamundaGroup(groupName)));
  }

  @Test
  void nonExistingGroupFindGroupByNameThrowsException() {
    final var groupName = "gr" + UUID.randomUUID();

    assertThrows(RuntimeException.class, () -> groupService.findGroupByName(groupName));
  }

  @Test
  void findAllGroupsReturnsAllGroups() {

    groupService.createGroup(new CamundaGroup("g" + UUID.randomUUID()));
    groupService.createGroup(new CamundaGroup("g" + UUID.randomUUID()));

    final var groups = groupService.findAllGroups();
    assertEquals(2, groups.size());
  }

  @Test
  void nonExistingGroupUpdateGroupThrowsException() {
    final var groupName = "gr" + UUID.randomUUID();

    assertThrows(
        RuntimeException.class,
        () -> groupService.updateGroup(groupName, new CamundaGroup(groupName)));
  }

  @Test
  void existingGroupUpdateGroupUpdated() {
    final var groupName = "gr" + UUID.randomUUID();
    final var newGroupName = "newGr" + UUID.randomUUID();
    groupService.createGroup(new CamundaGroup(groupName));

    groupService.updateGroup(groupName, new CamundaGroup(newGroupName));

    final var group = groupService.findGroupByName(newGroupName);
    assertNotNull(group);
  }
}
