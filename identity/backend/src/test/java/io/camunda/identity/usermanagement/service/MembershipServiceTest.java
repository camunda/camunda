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
    assertTrue(membershipService.getUserGroups(camundaUser).contains(group));
  }

  @Test
  void addUserToGroupUsingIdsAdded() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    final var createdUser =
        userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(createdUser.id(), group.id());

    assertTrue(membershipService.getUsersOfGroup(group.id()).contains(createdUser));
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
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    final CamundaUser createdUser =
        userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(createdUser.id(), group.id());
    assertThrows(
        RuntimeException.class,
        () -> membershipService.addUserToGroup(createdUser.id(), group.id()));
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
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    final CamundaUser createdUser =
        userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = new Group("group" + UUID.randomUUID());

    assertThrows(
        RuntimeException.class,
        () -> membershipService.addUserToGroup(createdUser.id(), group.id()));
  }

  @Test
  void nonExistingMemberRemoveUserFromGroupNoOp() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");

    userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));

    membershipService.removeUserFromGroup(camundaUserWithPassword, group);

    assertFalse(membershipService.getUsersOfGroup(group).contains(camundaUser));
  }

  @Test
  void nonExistingMemberRemoveUserFromGroupUsingIdsNoOp() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    final CamundaUser createdUser =
        userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.removeUserFromGroup(createdUser.id(), group.id());

    assertFalse(membershipService.getUsersOfGroup(group.id()).contains(camundaUserWithPassword));
  }

  @Test
  void existingMemberRemoveUserFromGroupRemoved() {
    final var camundaUserWithPassword =
        new CamundaUserWithPassword("user" + UUID.randomUUID(), "password");
    userService.createUser(camundaUserWithPassword);
    final var group = groupService.createGroup(new CamundaGroup("group" + UUID.randomUUID()));
    membershipService.addUserToGroup(camundaUserWithPassword, group);

    membershipService.removeUserFromGroup(camundaUserWithPassword, group);

    assertFalse(membershipService.getMembers(group).contains(camundaUserWithPassword));
    assertFalse(membershipService.getUserGroups(camundaUserWithPassword).contains(group));
  }

  @Test
  void existingMemberRemoveUserFromGroupUsingIdsRemoved() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    final CamundaUser createdUser =
        userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));
    membershipService.addUserToGroup(camundaUser, group);

    membershipService.removeUserFromGroup(createdUser.id(), group.id());

    assertFalse(membershipService.getUsersOfGroup(group.id()).contains(camundaUser));
    assertFalse(membershipService.getUserGroups(camundaUser).contains(group));
  }
}
