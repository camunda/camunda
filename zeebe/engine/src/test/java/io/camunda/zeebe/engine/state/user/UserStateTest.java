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

  @DisplayName("should return null if no user with given username exists")
  @Test
  void shouldReturnNullIfNoUserWithUsername() {
    // when
    final var persistedUser = userState.getUser("username" + UUID.randomUUID());

    // then
    assertThat(persistedUser).isNull();
  }

  @DisplayName("should create user if no user with given username exists")
  @Test
  void shouldCreateIfUsernameDoesNotExist() {
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

  @DisplayName("should create user throws exception when username already exists")
  @Test
  void shouldThrowExceptionInCreateIfUsernameDoesNotExist() {
    // given
    final UserRecord user =
        new UserRecord()
            .setUsername("username" + UUID.randomUUID())
            .setName("U")
            .setPassword("P")
            .setEmail("email" + UUID.randomUUID());
    userState.create(user);

    // when/then
    assertThatThrownBy(() -> userState.create(user))
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }
}
