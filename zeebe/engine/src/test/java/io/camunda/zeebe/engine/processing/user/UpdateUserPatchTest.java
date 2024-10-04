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
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UpdateUserPatchTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final UserRecord baseUser;
  private final UserRecord updatedUser;
  private final UserRecord expectedUser;

  public UpdateUserPatchTest(
      final UserRecord baseUser, final UserRecord updatedUser, final UserRecord expectedUser) {
    this.baseUser = baseUser;
    this.updatedUser = updatedUser;
    this.expectedUser = expectedUser;
  }

  @Test
  public void shouldUpdateAUserWithTheSpecifiedField() {
    final var userRecord =
        ENGINE
            .user()
            .newUser(baseUser.getUsername())
            .withUsername(baseUser.getUsername())
            .withName(baseUser.getName())
            .withEmail(baseUser.getEmail())
            .withPassword(baseUser.getPassword())
            .create();

    final var updatedUserRecord =
        ENGINE
            .user()
            .updateUser(userRecord.getKey(), updatedUser)
            .withUsername(baseUser.getUsername())
            .update()
            .getValue();

    assertThat(updatedUserRecord)
        .isNotNull()
        .hasFieldOrPropertyWithValue("username", expectedUser.getUsername())
        .hasFieldOrPropertyWithValue("name", expectedUser.getName())
        .hasFieldOrPropertyWithValue("email", expectedUser.getEmail())
        .hasFieldOrPropertyWithValue("password", expectedUser.getPassword());
  }

  @Parameterized.Parameters(name = "with base user: {0}, updated user: {1}, expected user: {2}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          // Update the name field
          {
            new UserRecord()
                .setUsername("user-1")
                .setName("Foo Bar")
                .setEmail("foo@bar")
                .setPassword("password"),
            new UserRecord().setName("Bar Foo"),
            new UserRecord()
                .setUsername("user-1")
                .setName("Bar Foo")
                .setEmail("foo@bar")
                .setPassword("password")
          },
          // Update the email field
          {
            new UserRecord()
                .setUsername("user-2")
                .setName("Foo Bar")
                .setEmail("foo@bar")
                .setPassword("password"),
            new UserRecord().setEmail("bar@foo"),
            new UserRecord()
                .setUsername("user-2")
                .setName("Foo Bar")
                .setEmail("bar@foo")
                .setPassword("password")
          },
          // Update the password field
          {
            new UserRecord()
                .setUsername("user-3")
                .setName("Foo Bar")
                .setEmail("foo@bar")
                .setPassword("password"),
            new UserRecord().setPassword("updated-password"),
            new UserRecord()
                .setUsername("user-3")
                .setName("Foo Bar")
                .setEmail("foo@bar")
                .setPassword("updated-password")
          },
          // Update the name and email field
          {
            new UserRecord()
                .setUsername("user-4")
                .setName("Foo Bar")
                .setEmail("foo@bar")
                .setPassword("password"),
            new UserRecord().setName("Bar Foo").setEmail("bar@foo"),
            new UserRecord()
                .setUsername("user-4")
                .setName("Bar Foo")
                .setEmail("bar@foo")
                .setPassword("password")
          }
        });
  }
}
