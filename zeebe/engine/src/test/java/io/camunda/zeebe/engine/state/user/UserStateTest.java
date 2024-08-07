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

  @DisplayName("should return null if a user with the given username exists")
  @Test
  void shouldReturnNullIfNoUserWithUsernameExists() {
    // when
    final var persistedUser = userState.getUser("username" + UUID.randomUUID());

    // then
    assertThat(persistedUser).isNull();
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
    assertThat(persistedUser).isEqualTo(user);
  }

  @DisplayName("should throw an exception when creating a user with a username that already exists")
  @Test
  void shouldThrowExceptionIfUsernameAlreadyExists() {
    final var username = "username" + UUID.randomUUID();
    // given
    final UserRecord user =
        new UserRecord()
            .setUsername(username)
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(user);

    // when/then
    assertThatThrownBy(() -> userState.create(user))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessage("Key " + username + " in ColumnFamily USERS already exists");
  }

  @DisplayName("should return the correct user by username")
  @Test
  void shouldReturnCorrectUserByUsername() {
    final var usernameOne = "username" + UUID.randomUUID();
    // given
    final UserRecord userOne =
        new UserRecord()
            .setUsername(usernameOne)
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(userOne);

    final var usernameTwo = "username" + UUID.randomUUID();
    final UserRecord userTwo =
        new UserRecord()
            .setUsername(usernameTwo)
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(userTwo);

    // when
    final var persistedUserOne = userState.getUser(usernameOne);
    final var persistedUserTwo = userState.getUser(usernameTwo);

    // then
    assertThat(persistedUserOne).isNotEqualTo(persistedUserTwo);

    assertThat(persistedUserOne.getUsername()).isEqualTo(usernameOne);
    assertThat(persistedUserTwo.getUsername()).isEqualTo(usernameTwo);
  }
}
