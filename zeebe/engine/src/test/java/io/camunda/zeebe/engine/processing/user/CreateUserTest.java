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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CreateUserTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

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

  @Test
  public void shouldRejectIfUsernameAlreadyExists() {
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

  @Test
  public void shouldAddDefaultPermissionsOnUserCreation() {
    // given
    final var username = UUID.randomUUID().toString();

    // when
    final long userKey =
        ENGINE
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create()
            .getKey();

    // then
    final var createdAuthorizationRecord =
        RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
            .withOwnerId(username)
            .getFirst()
            .getValue();

    final long authorizationKey =
        RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
            .withOwnerId(username)
            .getFirst()
            .getKey();

    // Verify authorization key is set and different from the user key
    Assertions.assertThat(authorizationKey)
        .describedAs("Authorization key should be different from user key")
        .isNotEqualTo(userKey);

    assertThat(createdAuthorizationRecord)
        .hasOwnerType(AuthorizationOwnerType.USER)
        .hasResourceType(AuthorizationResourceType.USER)
        .hasResourceId(username)
        .hasOnlyPermissionTypes(PermissionType.READ);

    // Verify that UserIntent.CREATE and UserIntent.CREATED events exist
    final var intents =
        RecordingExporter.userRecords()
            .withUsername(username)
            .limit(2)
            .map(Record::getIntent)
            .collect(Collectors.toList());

    Assertions.assertThat(intents).containsExactly(UserIntent.CREATE, UserIntent.CREATED);

    // Verify that AuthorizationIntent.CREATE and AuthorizationIntent.CREATED events exist
    final var authorizationIntents =
        RecordingExporter.authorizationRecords()
            .withOwnerId(username)
            .withAuthorizationKey(authorizationKey)
            .limit(2)
            .map(Record::getIntent)
            .toList();
    Assertions.assertThat(authorizationIntents)
        .containsExactly(AuthorizationIntent.CREATE, AuthorizationIntent.CREATED);
  }
}
