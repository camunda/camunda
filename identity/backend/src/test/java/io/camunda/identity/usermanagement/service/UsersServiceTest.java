/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.usermanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.identity.usermanagement.User;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
class UsersServiceTest {

  @Autowired private UsersService usersService;

  @Test
  void uniqueUserNameCreateUserCreated() {
    final var username = "user" + UUID.randomUUID();

    usersService.createUser(new User(username, "password"));

    final var existingUser = usersService.findUserByUsername(username);
    Assertions.assertNotNull(existingUser.orElse(null));
  }

  @Test
  void duplicateUserNameCreateUserException() {
    final var username = "user" + UUID.randomUUID();
    usersService.createUser(new User(username, "password"));

    assertThrows(
        RuntimeException.class, () -> usersService.createUser(new User(username, "password")));
  }

  @Test
  void existingUserDeleteUserDeleted() {
    final var username = "user" + UUID.randomUUID();
    usersService.createUser(new User(username, "password"));

    usersService.deleteUser(username);

    final var existingUser = usersService.findUserByUsername(username);
    assertTrue(existingUser.isEmpty());
  }

  @Test
  void nonExistingUserDeleteUserException() {
    final var username = "user" + UUID.randomUUID();

    assertThrows(RuntimeException.class, () -> usersService.deleteUser(username));
  }

  @Test
  void nonExistingUserFindUserByUsernameReturnsEmpty() {
    final var username = "user" + UUID.randomUUID();
    final var existingUser = usersService.findUserByUsername(username);
    assertTrue(existingUser.isEmpty());
  }

  @Test
  void findAllUsersReturnsAllUsers() {
    usersService.createUser(new User("user" + UUID.randomUUID(), "password"));
    usersService.createUser(new User("user" + UUID.randomUUID(), "password"));

    final var users = usersService.findAllUsers();
    assertEquals(2, users.size());
  }

  @Test
  void existingDisabledUserEnableUserEnabled() {
    final var username = "user" + UUID.randomUUID();
    usersService.createUser(new User(username, "password", false));

    usersService.enableUser(username);

    final var existingUser = usersService.findUserByUsername(username);
    assertTrue(existingUser.isPresent());
    assertTrue(existingUser.get().enabled());
  }

  @Test
  void existingEnabledUserDisableUserDisabled() {
    final var username = "user" + UUID.randomUUID();
    usersService.createUser(new User(username, "password"));

    usersService.disableUser(username);

    final var existingUser = usersService.findUserByUsername(username);
    assertTrue(existingUser.isPresent());
    assertFalse(existingUser.get().enabled());
  }

  @Test
  void existingDisabledUserDisableUserDisabled() {
    final var username = "user" + UUID.randomUUID();
    usersService.createUser(new User(username, "password", false));

    usersService.disableUser(username);

    final var existingUser = usersService.findUserByUsername(username);
    assertTrue(existingUser.isPresent());
    assertFalse(existingUser.get().enabled());
  }
}
