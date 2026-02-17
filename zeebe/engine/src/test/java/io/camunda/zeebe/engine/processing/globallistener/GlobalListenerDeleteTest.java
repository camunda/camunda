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
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GlobalListenerDeleteTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDeleteExistingListener() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventTypes("all").create();

    // when
    final var result = engine.globalListener().withId("my-id").delete();

    // then
    assertThat(result.getValue()).hasId("my-id");
  }

  @Test
  public void shouldNotDeleteListenerWithMissingId() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventTypes("all").create();

    // when
    final var rejection = engine.globalListener().expectRejection().delete();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldNotDeleteListenerIfItIsNotFound() {
    // given
    engine.globalListener().withId("my-id").withType("my-type").withEventTypes("creating").create();

    // when
    final var rejection = engine.globalListener().withId("my-other-id").expectRejection().delete();

    // then
    assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  // Authorization tests

  @Test
  public void shouldBeAuthorizedToDeleteListenerWithCorrectPermission() {
    engine.globalListener().withId("my-id").withType("my-type").withEventTypes("all").create();

    final var username = createUserWithPermissions(PermissionType.DELETE_TASK_LISTENER);
    engine.globalListener().withId("my-id").delete(username);
  }

  @Test
  public void shouldNotBeAuthorizedToDeleteListenerWithoutCorrectPermission() {
    engine.globalListener().withId("my-id").withType("my-type").withEventTypes("all").create();

    final var username =
        createUserWithPermissions(
            PermissionType.CREATE_TASK_LISTENER, PermissionType.UPDATE_TASK_LISTENER);
    final var rejection =
        engine.globalListener().withId("my-id").expectRejection().delete(username);
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
