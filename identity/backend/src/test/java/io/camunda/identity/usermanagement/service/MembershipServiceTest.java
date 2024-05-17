/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.Group;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
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
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(camundaUser, group);

    Assertions.assertTrue(membershipService.getMembers(group).contains(camundaUser));
    Assertions.assertTrue(membershipService.getUserGroups(camundaUser).contains(group));
  }

  @Test
  void duplicateUserAddUserToGroupThrowsException() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.addUserToGroup(camundaUser, group);
    Assertions.assertThrows(
        RuntimeException.class, () -> membershipService.addUserToGroup(camundaUser, group));
  }

  @Test
  void nonExistingGroupAddUserToGroupAdded() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = new Group("group" + UUID.randomUUID());

    Assertions.assertThrows(
        RuntimeException.class, () -> membershipService.addUserToGroup(camundaUser, group));
  }

  @Test
  void nonExistingMemberRemoveUserFromGroupNoOp() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));

    membershipService.removeUserFromGroup(camundaUser, group);

    Assertions.assertFalse(membershipService.getMembers(group).contains(camundaUser));
  }

  @Test
  void existingMemberRemoveUserFromGroupRemoved() {
    final var camundaUser = new CamundaUser("user" + UUID.randomUUID());
    userService.createUser(new CamundaUserWithPassword(camundaUser, "password"));
    final var group = groupService.createGroup(new Group("group" + UUID.randomUUID()));
    membershipService.addUserToGroup(camundaUser, group);

    membershipService.removeUserFromGroup(camundaUser, group);

    Assertions.assertFalse(membershipService.getMembers(group).contains(camundaUser));
    Assertions.assertFalse(membershipService.getUserGroups(camundaUser).contains(group));
  }
}
