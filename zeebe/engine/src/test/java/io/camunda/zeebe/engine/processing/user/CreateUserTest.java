/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class CreateUserTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @DisplayName("should create user if no user with given username exists")
  @Test
  public void shouldCreateUser() {
    // when
    final var username = UUID.randomUUID().toString();
    final var createdUserRecord =
        ENGINE
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    // then
    final var createdUser = createdUserRecord.getValue();
    Assertions.assertThat(createdUser)
        .isNotNull()
        .hasFieldOrPropertyWithValue("username", username)
        .hasFieldOrPropertyWithValue("name", "Foo Bar")
        .hasFieldOrPropertyWithValue("email", "foo@bar.com")
        .hasFieldOrPropertyWithValue("password", "password");
  }

  @DisplayName("should reject user create command when username already exists")
  @Test
  public void shouldNotDuplicate() {
    // given
    final var username = UUID.randomUUID().toString();
    final var createdUserRecord =
        ENGINE
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    // when
    final var duplicatedUserRecord =
        ENGINE
            .user()
            .newUser(username)
            .withName("Bar Foo")
            .withEmail("bar@foo.com")
            .withPassword("password")
            .expectRejection()
            .create();

    final var createdUser = createdUserRecord.getValue();
    Assertions.assertThat(createdUser)
        .isNotNull()
        .hasFieldOrPropertyWithValue("username", username);

    assertThat(duplicatedUserRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create user with username '"
                + username
                + "', but a user with this username already exists");
  }
}
