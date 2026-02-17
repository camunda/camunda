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

public class GlobalListenerCreateTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateNewListenerWithMinimalData() {
    final var result =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withEventTypes("creating", "updating")
            .create();
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
  public void shouldCreateNewListenerWithFullData() {
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
            .create();
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
  public void shouldNotCreateListenerWithMissingId() {
    final var rejection =
        engine
            .globalListener()
            .withType("my-type")
            .withEventTypes("creating")
            .expectRejection()
            .create();
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotCreateListenerWithMissingType() {
    final var rejection =
        engine
            .globalListener()
            .withId("my-id")
            .withEventTypes("creating")
            .expectRejection()
            .create();
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotCreateListenerWithMissingEventTypes() {
    final var rejection =
        engine.globalListener().withId("my-id").withType("my-type").expectRejection().create();
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotCreateListenerWithInvalidEventTypes() {
    final var rejection =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withEventTypes("creating")
            .withEventTypes("start")
            .expectRejection()
            .create();
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotCreateListenerIfItAlreadyExists() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventTypes("creating").create();

    // when
    final var rejection =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withEventTypes("creating")
            .expectRejection()
            .create();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.ALREADY_EXISTS);
  }

  // Authorization tests

  @Test
  public void shouldBeAuthorizedToCreateListenerWithCorrectPermission() {
    final var username = createUserWithPermissions(PermissionType.CREATE_TASK_LISTENER);
    engine
        .globalListener()
        .withId("my-id")
        .withType("my-type")
        .withEventTypes("creating", "updating")
        .create(username);
  }

  @Test
  public void shouldNotBeAuthorizedToCreateListenerWithoutCorrectPermission() {
    final var username =
        createUserWithPermissions(
            PermissionType.UPDATE_TASK_LISTENER, PermissionType.DELETE_TASK_LISTENER);
    final var rejection =
        engine
            .globalListener()
            .withId("my-id")
            .withType("my-type")
            .withEventTypes("creating", "updating")
            .expectRejection()
            .create(username);
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
