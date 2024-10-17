/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class UpdateUserTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @DisplayName("should update a user if the username exists")
  @Test
  public void shouldUpdateAUser() {
    final var username = UUID.randomUUID().toString();
    final var userRecord =
        ENGINE
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    final var updatedUser =
        ENGINE
            .user()
            .updateUser(userRecord.getKey())
            .withUsername(userRecord.getValue().getUsername())
            .withName("Bar Foo")
            .withEmail("foo@bar.blah")
            .withPassword("Foo Bar")
            .update()
            .getValue();

    assertThat(updatedUser)
        .isNotNull()
        .hasFieldOrPropertyWithValue("username", username)
        .hasFieldOrPropertyWithValue("name", "Bar Foo")
        .hasFieldOrPropertyWithValue("email", "foo@bar.blah")
        .hasFieldOrPropertyWithValue("password", "Foo Bar");
  }

  @DisplayName("should reject user update command when user key does not exist")
  @Test
  public void shouldRejectUserUpdateCommandWhenUserKeyDoesNotExist() {
    final var username = UUID.randomUUID().toString();
    final var userNotFoundRejection =
        ENGINE
            .user()
            .updateUser(-1L)
            .withUsername(username)
            .withName("Foo Bar")
            .withEmail("bar@foo")
            .withPassword("password")
            .expectRejection()
            .update();

    assertThat(userNotFoundRejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update user with username "
                + username
                + ", but a user with this username does not exist");
  }

  @DisplayName("should set the key on follow up commands to the user key")
  @Test
  public void shouldSetTheKeyOnFollowUpCommandsToTheUserKey() {
    final var username = UUID.randomUUID().toString();
    final var userRecord =
        ENGINE
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    ENGINE
        .user()
        .updateUser(userRecord.getKey())
        .withUsername(userRecord.getValue().getUsername())
        .withName("Bar Foo")
        .withEmail("foo@bar.blah")
        .withPassword("Foo Bar")
        .update()
        .getValue();

    Assertions.assertThat(
            RecordingExporter.userRecords(UserIntent.UPDATED)
                .withUserKey(userRecord.getKey())
                .limit(1)
                .getFirst()
                .getKey())
        .isEqualTo(userRecord.getKey());
  }
}
