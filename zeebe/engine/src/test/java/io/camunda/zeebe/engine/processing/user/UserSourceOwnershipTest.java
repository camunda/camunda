/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord.UserAttribute;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UserSourceOwnershipTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldStoreSourceOnCreate() {
    // when
    final var created = ENGINE.user().newUser(newUsername()).withSource("entra").create();

    // then
    assertThat(created.getValue().getSource()).isEqualTo("entra");
  }

  @Test
  public void shouldDefaultToNoSource() {
    // when
    final var created = ENGINE.user().newUser(newUsername()).create();

    // then
    assertThat(created.getValue().getSource()).isEmpty();
  }

  @Test
  public void shouldRejectCreateOfExistingUserOfOtherSource() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withSource("entra").create();

    // when
    final var rejection =
        ENGINE.user().newUser(username).withSource("keycloak").expectRejection().create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldUpdateUserOfSameSourceAndKeepSource() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withSource("entra").create();

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withSource("entra")
            .withName("Alice")
            .withChangedAttributes(UserAttribute.NAME)
            .update()
            .getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Alice");
    assertThat(updated.getSource()).isEqualTo("entra");
  }

  @Test
  public void shouldRejectUpdateOfUserOfOtherSourceAsNotFound() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withSource("entra").create();

    // when
    final var rejection =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withSource("keycloak")
            .withName("Mallory")
            .withChangedAttributes(UserAttribute.NAME)
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).doesNotContain("entra");
  }

  @Test
  public void shouldAdoptUnownedUserOnUpdateWithSourceMask() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withName("Alice").create();

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withSource("entra")
            .withExternalId("ext-1")
            .withChangedAttributes(UserAttribute.SOURCE, UserAttribute.EXTERNAL_ID)
            .update()
            .getValue();

    // then
    assertThat(updated.getSource()).isEqualTo("entra");
    assertThat(updated.getName()).isEqualTo("Alice");
    assertThat(updated.getExternalId()).isEqualTo("ext-1");
  }

  @Test
  public void shouldRejectUpdateOfUnownedUserWithoutAdoption() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).create();

    // when
    final var rejection =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withSource("entra")
            .withName("Alice")
            .withChangedAttributes(UserAttribute.NAME)
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("not owned by source 'entra'");
  }

  @Test
  public void shouldRejectRestUpdateOfOwnedUser() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withSource("entra").create();

    // when
    final var rejection =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withName("Alice")
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("managed by identity provider");
  }

  @Test
  public void shouldAllowRestUpdateOfUnownedUser() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).create();

    // when
    final var updated =
        ENGINE.user().updateUser().withUsername(username).withName("Alice").update().getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Alice");
    assertThat(updated.getSource()).isEmpty();
  }

  @Test
  public void shouldDeleteUserOfSameSource() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withSource("entra").create();

    // when
    ENGINE.user().deleteUser(username).withSource("entra").delete();

    // then
    assertThat(RecordingExporter.userRecords(UserIntent.DELETED).withUsername(username).exists())
        .isTrue();
  }

  @Test
  public void shouldRejectDeleteOfUserOfOtherSourceAsNotFound() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withSource("entra").create();

    // when
    final var rejection =
        ENGINE.user().deleteUser(username).withSource("keycloak").expectRejection().delete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectDeleteOfUnownedUserWithoutAdoption() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).create();

    // when
    final var rejection =
        ENGINE.user().deleteUser(username).withSource("entra").expectRejection().delete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("not owned by source");
  }

  @Test
  public void shouldDeleteUnownedUserWithAdoption() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).create();

    // when
    ENGINE
        .user()
        .deleteUser(username)
        .withSource("entra")
        .withChangedAttributes(UserAttribute.SOURCE)
        .delete();

    // then
    assertThat(RecordingExporter.userRecords(UserIntent.DELETED).withUsername(username).exists())
        .isTrue();
  }

  @Test
  public void shouldRejectRestDeleteOfOwnedUser() {
    // given
    final var username = newUsername();
    ENGINE.user().newUser(username).withSource("entra").create();

    // when
    final var rejection = ENGINE.user().deleteUser(username).expectRejection().delete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("managed by identity provider");
  }

  private static String newUsername() {
    return UUID.randomUUID().toString();
  }
}
