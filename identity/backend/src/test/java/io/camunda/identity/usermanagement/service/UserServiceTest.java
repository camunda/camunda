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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.identity.CamundaSpringBootTest;
import io.camunda.identity.security.CamundaUserDetailsManager;
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@CamundaSpringBootTest
class UserServiceTest {

  @Autowired private UserService userService;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CamundaUserDetailsManager camundaUserDetailsManager;

  @Test
  void uniqueUsernameCreateUserCreated() {
    final var username = "user" + UUID.randomUUID();

    final var user = userService.createUser(new CamundaUserWithPassword(username, "password"));

    final var existingUser = userService.findUserById(user.getId());
    Assertions.assertNotNull(existingUser);
    Assertions.assertEquals(username, existingUser.getUsername());
  }

  @Test
  void duplicateUsernameCreateUserException() {
    final var username = "user" + UUID.randomUUID();
    userService.createUser(new CamundaUserWithPassword(username, "password"));

    assertThrows(
        RuntimeException.class,
        () -> userService.createUser(new CamundaUserWithPassword(username, "password")));
  }

  @Test
  void existingUserDeleteUserDeleted() {
    final var username = "user" + UUID.randomUUID();
    final CamundaUser user =
        userService.createUser(new CamundaUserWithPassword(username, "password"));

    userService.deleteUser(user.getId());

    assertThrows(RuntimeException.class, () -> userService.findUserById(user.getId()));
  }

  @Test
  void nonExistingUserDeleteUserException() {
    assertThrows(RuntimeException.class, () -> userService.deleteUser(1000L));
  }

  @Test
  void nonExistingUserFindUserByUsernameThrowsException() {
    final var username = "user" + UUID.randomUUID();
    assertThrows(RuntimeException.class, () -> userService.findUserByUsername(username));
  }

  @Test
  void findAllUsersReturnsAllUsers() {
    final var count = userService.findAllUsers().size();
    userService.createUser(new CamundaUserWithPassword("user" + UUID.randomUUID(), "password"));
    userService.createUser(new CamundaUserWithPassword("user" + UUID.randomUUID(), "password"));

    final var users = userService.findAllUsers();
    assertEquals(2 + count, users.size());
  }

  @Test
  void nonExistingUserUpdateUserThrowsException() {
    final var username = "user" + UUID.randomUUID();
    final var user = new CamundaUserWithPassword(0L, username, "email", false, "password");
    assertThrows(RuntimeException.class, () -> userService.updateUser(0L, user));
  }

  @Test
  void existingUserEnableUpdateUserEnabled() {
    final var username = "user" + UUID.randomUUID();
    final var user =
        userService.createUser(new CamundaUserWithPassword(username, "email", false, "password"));

    userService.updateUser(
        user.getId(),
        new CamundaUserWithPassword(user.getId(), username, "email", true, "password"));

    final var existingUser = userService.findUserByUsername(username);
    assertTrue(existingUser.isEnabled());
  }

  @Test
  void existingUserNewEmailUpdateUserEmailChanged() {
    final var username = "user" + UUID.randomUUID();
    final var user =
        userService.createUser(new CamundaUserWithPassword(username, "email", false, "password"));

    userService.updateUser(
        user.getId(),
        new CamundaUserWithPassword(user.getId(), username, "email2", true, "password"));

    final var existingUser = userService.findUserByUsername(username);
    assertEquals("email2", existingUser.getEmail());
  }

  @Test
  void existingUserEmptyEmailUpdateUserEmailChanged() {
    final var username = "user" + UUID.randomUUID();
    final CamundaUser user =
        userService.createUser(
            new CamundaUserWithPassword(0L, username, "email", false, "password"));

    userService.updateUser(
        user.getId(), new CamundaUserWithPassword(user.getId(), username, null, true, "password"));

    final var existingUser = userService.findUserById(user.getId());
    assertNull(existingUser.getEmail());
  }

  @Test
  void existingUserDisableUpdateUserDisabled() {
    final var username = "user" + UUID.randomUUID();
    final CamundaUser user =
        userService.createUser(new CamundaUserWithPassword(username, "password"));

    userService.updateUser(
        user.getId(),
        new CamundaUserWithPassword(user.getId(), username, "email", false, "password"));

    final var existingUser = userService.findUserById(user.getId());
    assertFalse(existingUser.isEnabled());
  }

  @Test
  void noChangePassUpdateUserPasswordNotChanged() {
    final var username = "user" + UUID.randomUUID();
    final var user =
        userService.createUser(new CamundaUserWithPassword(username, "email", false, "password"));
    final var userWithPassword =
        new CamundaUserWithPassword(user.getId(), username, "email", false, "password");

    userService.updateUser(user.getId(), userWithPassword);

    final var updatedUser = camundaUserDetailsManager.loadUserByUsername(username);
    assertTrue(passwordEncoder.matches(userWithPassword.getPassword(), updatedUser.getPassword()));
  }

  @Test
  void newPassUpdateUserPasswordChanged() {
    final var username = "user" + UUID.randomUUID();
    final var password = "password";
    final var newPassword = "password1";
    final var user =
        userService.createUser(new CamundaUserWithPassword(username, "email", false, password));

    userService.updateUser(
        user.getId(),
        new CamundaUserWithPassword(user.getId(), username, "email", false, newPassword));

    final var updatedUser = camundaUserDetailsManager.loadUserByUsername(username);
    assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPassword()));
    assertFalse(passwordEncoder.matches(password, updatedUser.getPassword()));
  }
}
