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
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord.UserAttribute;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UpdateUserMaskedTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldPersistExternalIdAndActiveOnCreate() {
    // given
    final var username = UUID.randomUUID().toString();

    // when
    final var created =
        ENGINE.user().newUser(username).withExternalId("ext-1").withActive(false).create();

    // then
    assertThat(created.getValue().getExternalId()).isEqualTo("ext-1");
    assertThat(created.getValue().isActive()).isFalse();
  }

  @Test
  public void shouldCreateUserActiveWithEmptyExternalIdByDefault() {
    // when
    final var created = ENGINE.user().newUser(UUID.randomUUID().toString()).create();

    // then
    assertThat(created.getValue().getExternalId()).isEmpty();
    assertThat(created.getValue().isActive()).isTrue();
  }

  @Test
  public void shouldOnlyChangeMaskedAttributes() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE
        .user()
        .newUser(username)
        .withName("Foo Bar")
        .withEmail("foo@bar.com")
        .withPassword("password")
        .withExternalId("ext-1")
        .create();

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withActive(false)
            .withChangedAttributes(UserAttribute.ACTIVE)
            .update()
            .getValue();

    // then
    assertThat(updated.isActive()).isFalse();
    assertThat(updated.getName()).isEqualTo("Foo Bar");
    assertThat(updated.getEmail()).isEqualTo("foo@bar.com");
    assertThat(updated.getPassword()).isEqualTo("password");
    assertThat(updated.getExternalId()).isEqualTo("ext-1");
  }

  @Test
  public void shouldClearMaskedAttributesWithEmptyValues() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE
        .user()
        .newUser(username)
        .withName("Foo Bar")
        .withEmail("foo@bar.com")
        .withExternalId("ext-1")
        .create();

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withEmail("")
            .withExternalId("ext-2")
            .withChangedAttributes(UserAttribute.EMAIL, UserAttribute.EXTERNAL_ID)
            .update()
            .getValue();

    // then
    assertThat(updated.getEmail()).isEmpty();
    assertThat(updated.getExternalId()).isEqualTo("ext-2");
    assertThat(updated.getName()).isEqualTo("Foo Bar");
    assertThat(updated.isActive()).isTrue();
  }

  @Test
  public void shouldKeepActiveAndExternalIdOnLegacyUpdate() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE
        .user()
        .newUser(username)
        .withName("Foo Bar")
        .withExternalId("ext-1")
        .withActive(false)
        .create();

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withName("Bar Foo")
            .withEmail("bar@foo.com")
            .update()
            .getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Bar Foo");
    assertThat(updated.getEmail()).isEqualTo("bar@foo.com");
    assertThat(updated.getExternalId()).isEqualTo("ext-1");
    assertThat(updated.isActive()).isFalse();
  }

  @Test
  public void shouldWriteUpdatedEventWithoutMask() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE.user().newUser(username).create();

    // when
    ENGINE
        .user()
        .updateUser()
        .withUsername(username)
        .withName("Bar Foo")
        .withChangedAttributes(UserAttribute.NAME)
        .update();

    // then
    final var event =
        RecordingExporter.userRecords(UserIntent.UPDATED).withUsername(username).getFirst();
    assertThat(((UserRecord) event.getValue()).getChangedAttributes()).isEmpty();
  }

  @Test
  public void shouldPersistAttributesOnCreate() {
    // when
    final var created =
        ENGINE
            .user()
            .newUser(UUID.randomUUID().toString())
            .withAttributes(Map.of("department", "sales", "title", "Engineer"))
            .create();

    // then
    assertThat(created.getValue().getAttributes())
        .containsExactlyInAnyOrderEntriesOf(Map.of("department", "sales", "title", "Engineer"));
  }

  @Test
  public void shouldReplaceAttributesWhenMasked() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE
        .user()
        .newUser(username)
        .withAttributes(Map.of("department", "sales", "title", "Engineer"))
        .create();

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withAttributes(Map.of("clearance", "high"))
            .withChangedAttributes(UserAttribute.ATTRIBUTES)
            .update()
            .getValue();

    // then
    assertThat(updated.getAttributes()).containsExactlyEntriesOf(Map.of("clearance", "high"));
  }

  @Test
  public void shouldMergeAttributesWhenMasked() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE
        .user()
        .newUser(username)
        .withName("Foo Bar")
        .withAttributes(Map.of("department", "sales", "title", "Engineer", "addresses", List.of()))
        .create();
    final var patch = new HashMap<String, Object>();
    patch.put("department", "support");
    patch.put("clearance", Map.of("level", 3));
    patch.put("title", null);

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withAttributes(patch)
            .withChangedAttributes(UserAttribute.ATTRIBUTES_MERGE)
            .update()
            .getValue();

    // then
    assertThat(updated.getAttributes())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "department", "support",
                "clearance", Map.of("level", 3),
                "addresses", List.of()));
    assertThat(updated.getName()).isEqualTo("Foo Bar");
  }

  @Test
  public void shouldKeepAttributesOnLegacyUpdate() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE.user().newUser(username).withAttributes(Map.of("department", "sales")).create();

    // when
    final var updated =
        ENGINE.user().updateUser().withUsername(username).withName("Bar Foo").update().getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Bar Foo");
    assertThat(updated.getAttributes()).containsExactlyEntriesOf(Map.of("department", "sales"));
  }

  @Test
  public void shouldKeepAttributesOnMaskedUpdateOfOtherFields() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE.user().newUser(username).withAttributes(Map.of("department", "sales")).create();

    // when
    final var updated =
        ENGINE
            .user()
            .updateUser()
            .withUsername(username)
            .withActive(false)
            .withChangedAttributes(UserAttribute.ACTIVE)
            .update()
            .getValue();

    // then
    assertThat(updated.getAttributes()).containsExactlyEntriesOf(Map.of("department", "sales"));
  }
}
