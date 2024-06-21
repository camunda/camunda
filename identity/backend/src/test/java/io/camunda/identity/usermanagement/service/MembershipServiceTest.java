/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringBootTest
public class MembershipServiceTest {
  @Autowired private MembershipService membershipService;

  @Autowired private GroupService groupService;
  @Autowired private UserService userService;

  @Test
  void addUserToGroupAdded() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    final var createdUser = userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(camundaUserWithPassword, group);

    assertTrue(membershipService.getUsersOfGroup(group).contains(createdUser));
    assertTrue(membershipService.getUserGroups(camundaUserWithPassword).contains(group));
  }

  @Test
  void addUserToGroupUsingIdsAdded() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    final var createdUser = userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));

    membershipService.addUserToGroupByIds(createdUser.getId(), group.id());

    assertTrue(membershipService.getUsersOfGroupById(group.id()).contains(createdUser));
    assertTrue(membershipService.getUserGroups(camundaUserWithPassword).contains(group));
  }

  @Test
  void duplicateUserAddUserToGroupThrowsException() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(camundaUserWithPassword, group);
    assertThrows(
        RuntimeException.class,
        () -> membershipService.addUserToGroup(camundaUserWithPassword, group));
  }

  @Test
  void duplicateUserAddUserToGroupUsingIdsThrowsException() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    final CamundaUser createdUser = userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));

    membershipService.addUserToGroupByIds(createdUser.getId(), group.id());
    assertThrows(
        RuntimeException.class,
        () -> membershipService.addUserToGroupByIds(createdUser.getId(), group.id()));
  }

  @Test
  void nonExistingGroupAddUserToGroupThrowsException() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    userService.createUser(camundaUserWithPassword);
    final var group = new CamundaGroup("group" + UUID.randomUUID());

    assertThrows(
        RuntimeException.class,
        () -> membershipService.addUserToGroup(camundaUserWithPassword, group));
  }

  @Test
  void nonExistingGroupAddUserToGroupUsingIdsThrowsException() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    final CamundaUser createdUser = userService.createUser(camundaUserWithPassword);
    final var group = new CamundaGroup("group" + UUID.randomUUID());

    assertThrows(
        RuntimeException.class,
        () -> membershipService.addUserToGroupByIds(createdUser.getId(), group.id()));
  }

  @Test
  void nonExistingMemberRemoveUserFromGroupNoOp() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));

    membershipService.removeUserFromGroup(camundaUserWithPassword, group);

    assertFalse(membershipService.getUsersOfGroup(group).contains(camundaUserWithPassword));
  }

  @Test
  void nonExistingMemberRemoveUserFromGroupUsingIdsNoOp() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    final CamundaUser createdUser = userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));

    membershipService.removeUserFromGroupByIds(createdUser.getId(), group.id());

    assertFalse(
        membershipService.getUsersOfGroupById(group.id()).contains(camundaUserWithPassword));
  }

  @Test
  void existingMemberRemoveUserFromGroupRemoved() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));
    membershipService.addUserToGroup(camundaUserWithPassword, group);

    membershipService.removeUserFromGroup(camundaUserWithPassword, group);

    assertFalse(membershipService.getUsersOfGroup(group).contains(camundaUserWithPassword));
    assertFalse(membershipService.getUserGroups(camundaUserWithPassword).contains(group));
  }

  @Test
  void existingMemberRemoveUserFromGroupUsingIdsRemoved() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    final CamundaUser createdUser = userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));
    membershipService.addUserToGroup(camundaUserWithPassword, group);

    membershipService.removeUserFromGroupByIds(createdUser.getId(), group.id());

    assertFalse(
        membershipService.getUsersOfGroupById(group.id()).contains(camundaUserWithPassword));
    assertFalse(membershipService.getUserGroups(camundaUserWithPassword).contains(group));
  }
}
