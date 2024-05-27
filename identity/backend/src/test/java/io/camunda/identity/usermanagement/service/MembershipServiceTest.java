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

import io.camunda.identity.user.CamundaUser;
import io.camunda.identity.user.CamundaUserWithPassword;
import io.camunda.identity.user.Group;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class MembershipServiceTest {
  @Autowired private MembershipService membershipService;

  @Autowired private GroupService groupService;
  @Autowired private UserService userService;

  @Test
  void addUserToGroupAdded() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    final var createdUser =
        userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(camundaUser, group);

    assertTrue(membershipService.getMembers(group).contains(createdUser));
    assertTrue(membershipService.getUserGroups(camundaUser).contains(group));
  }

  @Test
  void duplicateUserAddUserToGroupThrowsException() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(camundaUser, group);
    assertThrows(
        RuntimeException.class, () -> membershipService.addUserToGroup(camundaUser, group));
  }

  @Test
  void nonExistingGroupAddUserToGroupAdded() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = new Group("group" + UUID.randomUUID());

    assertThrows(
        RuntimeException.class, () -> membershipService.addUserToGroup(camundaUser, group));
  }

  @Test
  void nonExistingMemberRemoveUserFromGroupNoOp() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.removeUserFromGroup(camundaUser, group);

    assertFalse(membershipService.getMembers(group).contains(camundaUser));
  }

  @Test
  void existingMemberRemoveUserFromGroupRemoved() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));
    membershipService.addUserToGroup(camundaUser, group);

    membershipService.removeUserFromGroup(camundaUser, group);

    assertFalse(membershipService.getMembers(group).contains(camundaUser));
    assertFalse(membershipService.getUserGroups(camundaUser).contains(group));
  }
}
