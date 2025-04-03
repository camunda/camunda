/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserStateTest {
  private MutableProcessingState processingState;
  private MutableUserState userState;

  @BeforeEach
  public void setup() {
    userState = processingState.getUserState();
  }

  @DisplayName("should return empty optional if a user with the given username exists")
  @Test
  void shouldReturnEmptyOptionalIfNoUserWithUsernameExists() {
    // when
    final var persistedUser = userState.getUser("username" + UUID.randomUUID());

    // then
    assertThat(persistedUser).isEmpty();
  }

  @DisplayName("should create user if no user with the given username exists")
  @Test
  void shouldCreateUserIfUsernameDoesNotExist() {
    // when
    final UserRecord user =
        new UserRecord()
            .setUsername("username" + UUID.randomUUID())
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(user);

    // then
    final var persistedUser = userState.getUser(user.getUsername());
    assertThat(persistedUser).isPresent();
    assertThat(persistedUser.get().getUser()).isEqualTo(user);
  }

  @DisplayName("should throw an exception when creating a user with a username that already exists")
  @Test
  void shouldThrowExceptionIfUsernameAlreadyExists() {
    final var username = "username" + UUID.randomUUID();
    // given
    final UserRecord user =
        new UserRecord()
            .setUserKey(2L)
            .setUsername(username)
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(user);

    // when/then
    assertThatThrownBy(() -> userState.create(user))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessage("Key %s in ColumnFamily USERS already exists".formatted(username));
  }

  @DisplayName("should return the correct user by username")
  @Test
  void shouldReturnCorrectUserByUsername() {
    final var usernameOne = "username" + UUID.randomUUID();
    // given
    final UserRecord userOne =
        new UserRecord()
            .setUserKey(1L)
            .setUsername(usernameOne)
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(userOne);

    final var usernameTwo = "username" + UUID.randomUUID();
    final UserRecord userTwo =
        new UserRecord()
            .setUserKey(2L)
            .setUsername(usernameTwo)
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(userTwo);

    // when
    final var persistedUserOne = userState.getUser(usernameOne).get();
    final var persistedUserTwo = userState.getUser(usernameTwo).get();

    // then
    assertThat(persistedUserOne).isNotEqualTo(persistedUserTwo);

    assertThat(persistedUserOne.getUsername()).isEqualTo(usernameOne);
    assertThat(persistedUserTwo.getUsername()).isEqualTo(usernameTwo);
  }

  @DisplayName("should update a user")
  @Test
  void shouldUpdateAUser() {
    final var userKey = 1L;
    final var username = "username" + UUID.randomUUID();
    final var name = "name" + UUID.randomUUID();
    final var password = "password" + UUID.randomUUID();
    final var email = "email" + UUID.randomUUID();

    final UserRecord user =
        new UserRecord()
            .setUserKey(userKey)
            .setUsername(username)
            .setName(name)
            .setPassword(password)
            .setEmail(email);
    userState.create(user);

    final var persistedUserBeforeUpdate = userState.getUser(username).get();
    assertThat(persistedUserBeforeUpdate.getName()).isEqualTo(name);
    assertThat(persistedUserBeforeUpdate.getPassword()).isEqualTo(password);
    assertThat(persistedUserBeforeUpdate.getEmail()).isEqualTo(email);

    final var updatedName = "name" + UUID.randomUUID();
    final var updatedPassword = "password" + UUID.randomUUID();
    final var updatedEmail = "email" + UUID.randomUUID();

    user.setName(updatedName);
    user.setPassword(updatedPassword);
    user.setEmail(updatedEmail);

    userState.update(user);

    final var persistedUserAfterUpdate = userState.getUser(username).get();
    assertThat(persistedUserAfterUpdate.getName()).isEqualTo(updatedName);
    assertThat(persistedUserAfterUpdate.getPassword()).isEqualTo(updatedPassword);
    assertThat(persistedUserAfterUpdate.getEmail()).isEqualTo(updatedEmail);
  }

  @DisplayName("should delete a user")
  @Test
  void shouldDeleteAUser() {
    final var userKey = 1L;
    final var username = "username" + UUID.randomUUID();
    final var name = "name" + UUID.randomUUID();
    final var password = "password" + UUID.randomUUID();
    final var email = "email" + UUID.randomUUID();

    final UserRecord user =
        new UserRecord()
            .setUserKey(userKey)
            .setUsername(username)
            .setName(name)
            .setPassword(password)
            .setEmail(email);
    userState.create(user);

    assertThat(userState.getUser(username)).isNotEmpty();

    userState.delete(username);

    assertThat(userState.getUser(username)).isEmpty();
  }

  @Test
  void shouldReturnUserByKey() {
    // given
    final long userKey = 1L;
    final var username = "username";
    final var name = "Foo";
    final var email = "foo@bar.com";
    final var password = "password";
    userState.create(
        new UserRecord()
            .setUserKey(userKey)
            .setUsername(username)
            .setName(name)
            .setEmail(email)
            .setPassword(password));

    // when
    final var persistedUser = userState.getUser(userKey);

    // then
    assertThat(persistedUser).isNotEmpty();
    assertThat(persistedUser.get())
        .extracting(
            PersistedUser::getUserKey,
            PersistedUser::getUsername,
            PersistedUser::getName,
            PersistedUser::getEmail,
            PersistedUser::getPassword)
        .containsExactly(userKey, username, name, email, password);
  }

  @Test
  void shouldReturnEmptyOptionalIfUserByKeyNotFound() {
    // given
    final var username = "username";
    userState.create(new UserRecord().setUserKey(1L).setUsername(username));

    // when
    final var persistedUser = userState.getUser(2L);

    // then
    assertThat(persistedUser).isEmpty();
  }
}
