/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GlobalListenerUpdateTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldUpdateNewListenerWithMinimalData() {
    // given
    engine
        .globalListener()
        .withId("my-id")
        .withType("my-old-type")
        .withRetries(5)
        .withEventTypes("all")
        .withAfterNonGlobal(true)
        .withPriority(100)
        .withSource(GlobalListenerSource.API)
        .withListenerType(GlobalListenerType.USER_TASK)
        .create();

    // when
    final var result =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withEventTypes("creating", "updating")
            .update();

    // then
    assertThat(result.getValue())
        .hasId("my-id")
        .hasType("my-type")
        .hasRetries(GlobalListenerRecordValue.DEFAULT_RETRIES)
        .hasEventTypes("creating", "updating")
        .isNotAfterNonGlobal()
        .hasPriority(GlobalListenerRecordValue.DEFAULT_PRIORITY)
        .hasSource(GlobalListenerRecordValue.DEFAULT_SOURCE)
        .hasListenerType(GlobalListenerRecordValue.DEFAULT_LISTENER_TYPE);
  }

  @Test
  public void shouldUpdateNewListenerWithFullData() {
    // given
    engine.globalListener().withId("my-id").withType("my-old-type").withEventTypes("all").create();

    // when
    final var result =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withRetries(5)
            .withEventTypes("creating", "updating")
            .withAfterNonGlobal(true)
            .withPriority(100)
            .withSource(GlobalListenerSource.API)
            .withListenerType(GlobalListenerType.USER_TASK)
            .update();

    // then
    assertThat(result.getValue())
        .hasId("my-id")
        .hasType("my-type")
        .hasRetries(5)
        .hasEventTypes("creating", "updating")
        .isAfterNonGlobal()
        .hasPriority(100)
        .hasSource(GlobalListenerSource.API)
        .hasListenerType(GlobalListenerType.USER_TASK);
  }

  @Test
  public void shouldNotUpdateListenerWithMissingId() {
    // given
    engine.globalListener().withId("my-id").withType("my-old-type").withEventTypes("all").create();

    // when
    final var rejection =
        engine
            .globalListener()
            .withType("my-type")
            .withEventTypes("creating")
            .expectRejection()
            .update();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotUpdateListenerWithMissingType() {
    // given
    engine.globalListener().withId("my-id").withType("my-old-type").withEventTypes("all").create();

    // when
    final var rejection =
        engine
            .globalListener()
            .withId("my-id")
            .withEventTypes("creating")
            .expectRejection()
            .update();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotUpdateListenerWithMissingEventTypes() {
    // given
    engine.globalListener().withId("my-id").withType("my-old-type").withEventTypes("all").create();

    // when
    final var rejection =
        engine.globalListener().withId("my-id").withType("my-type").expectRejection().update();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotUpdateListenerWithInvalidEventTypes() {
    // given
    engine.globalListener().withId("my-id").withType("my-old-type").withEventTypes("all").create();

    // when
    final var rejection =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withEventTypes("creating", "start")
            .expectRejection()
            .update();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotUpdateListenerIfItIsNotFound() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventTypes("creating").create();

    // when
    final var rejection =
        engine
            .globalListener()
            .withId("my-other-id")
            .withType("my-type")
            .withEventTypes("creating")
            .expectRejection()
            .update();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  // Authorization tests

  @Test
  public void shouldBeAuthorizedToUpdateListenerWithCorrectPermission() {
    engine.globalListener().withId("my-id").withType("my-old-type").withEventTypes("all").create();

    final var username = createUserWithPermissions(PermissionType.UPDATE_TASK_LISTENER);
    engine
        .globalListener()
        .withId("my-id")
        .withType("my-type")
        .withEventTypes("creating")
        .update(username);
  }

  @Test
  public void shouldNotBeAuthorizedToUpdateListenerWithoutCorrectPermission() {
    engine.globalListener().withId("my-id").withType("my-old-type").withEventTypes("all").create();

    final var username =
        createUserWithPermissions(
            PermissionType.CREATE_TASK_LISTENER, PermissionType.DELETE_TASK_LISTENER);
    final var rejection =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withEventTypes("creating")
            .expectRejection()
            .update(username);
    assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
  }

  private String createUserWithoutPermissions() {
    final var user = engine.user().newUser(UUID.randomUUID().toString()).create().getValue();
    return user.getUsername();
  }

  private String createUserWithPermissions(final PermissionType... permissions) {
    final var username = createUserWithoutPermissions();
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissions)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.GLOBAL_LISTENER)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create();
    return username;
  }
}
