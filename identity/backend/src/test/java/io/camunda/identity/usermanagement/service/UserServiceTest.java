/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.identity.authentication.user.CamundaUserDetailsManager;
import io.camunda.identity.user.CamundaUser;
import io.camunda.identity.user.CamundaUserWithPassword;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
class UserServiceTest {

  @Autowired private UserService userService;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CamundaUserDetailsManager camundaUserDetailsManager;

  @Test
  void uniqueUsernameCreateUserCreated() {
    final var username = "user" + UUID.randomUUID();

    userService.createUser(new CamundaUserWithPassword(new CamundaUser(username), "password"));

    final var existingUser = userService.findUserByUsername(username);
    Assertions.assertNotNull(existingUser);
  }

  @Test
  void duplicateUsernameCreateUserException() {
    final var username = "user" + UUID.randomUUID();
    userService.createUser(new CamundaUserWithPassword(new CamundaUser(username), "password"));

    assertThrows(
        RuntimeException.class,
        () ->
            userService.createUser(
                new CamundaUserWithPassword(new CamundaUser(username), "password")));
  }

  @Test
  void existingUserDeleteUserDeleted() {
    final var username = "user" + UUID.randomUUID();
    userService.createUser(new CamundaUserWithPassword(new CamundaUser(username), "password"));

    userService.deleteUser(username);

    Assertions.assertThrows(RuntimeException.class, () -> userService.findUserByUsername(username));
  }

  @Test
  void nonExistingUserDeleteUserException() {
    final var username = "user" + UUID.randomUUID();

    assertThrows(RuntimeException.class, () -> userService.deleteUser(username));
  }

  @Test
  void nonExistingUserFindUserByUsernameThrowsException() {
    final var username = "user" + UUID.randomUUID();
    Assertions.assertThrows(RuntimeException.class, () -> userService.findUserByUsername(username));
  }

  @Test
  void findAllUsersReturnsAllUsers() {
    userService.createUser(
        new CamundaUserWithPassword(new CamundaUser("user" + UUID.randomUUID()), "password"));
    userService.createUser(
        new CamundaUserWithPassword(new CamundaUser("user" + UUID.randomUUID()), "password"));

    final var users = userService.findAllUsers();

    // Set to 3 due to a demo user being initialized.
    assertEquals(3, users.size());
  }

  @Test
  void nonExistingUserUpdateUserThrowsException() {
    final var username = "user" + UUID.randomUUID();
    final var user = new CamundaUserWithPassword(new CamundaUser(username, false), "password");
    Assertions.assertThrows(RuntimeException.class, () -> userService.updateUser(username, user));
  }

  @Test
  void existingUserEnableUpdateUserEnabled() {
    final var username = "user" + UUID.randomUUID();
    userService.createUser(
        new CamundaUserWithPassword(new CamundaUser(username, false), "password"));

    userService.updateUser(
        username, new CamundaUserWithPassword(new CamundaUser(username, true), "password"));

    final var existingUser = userService.findUserByUsername(username);
    assertTrue(existingUser.enabled());
  }

  @Test
  void existingUserDisableUpdateUserDisabled() {
    final var username = "user" + UUID.randomUUID();
    userService.createUser(new CamundaUserWithPassword(new CamundaUser(username), "password"));

    userService.updateUser(
        username, new CamundaUserWithPassword(new CamundaUser(username, false), "password"));

    final var existingUser = userService.findUserByUsername(username);
    assertFalse(existingUser.enabled());
  }

  @Test
  void noChangePassUpdateUserPasswordNotChanged() {
    final var username = "user" + UUID.randomUUID();
    final var user = new CamundaUserWithPassword(new CamundaUser(username, false), "password");
    userService.createUser(user);

    userService.updateUser(username, user);

    final var updatedUser = camundaUserDetailsManager.loadUserByUsername(username);
    assertTrue(passwordEncoder.matches(user.password(), updatedUser.getPassword()));
  }

  @Test
  void newPassUpdateUserPasswordChanged() {
    final var username = "user" + UUID.randomUUID();
    final var password = "password";
    final var newPassword = "password1";
    userService.createUser(new CamundaUserWithPassword(new CamundaUser(username, false), password));

    userService.updateUser(
        username, new CamundaUserWithPassword(new CamundaUser(username, false), newPassword));

    final var updatedUser = camundaUserDetailsManager.loadUserByUsername(username);
    assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPassword()));
    assertFalse(passwordEncoder.matches(password, updatedUser.getPassword()));
  }
}
