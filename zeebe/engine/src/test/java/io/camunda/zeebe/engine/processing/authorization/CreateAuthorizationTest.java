/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class CreateAuthorizationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @DisplayName(
      "should create an authorization if no authorization for owner and resource pair exists")
  @Test
  public void shouldCreateAuthorization() {
    // when
    final var owner = "owner" + UUID.randomUUID();
    final var permissions = List.of("write:*");
    final var createdAuthorizationRecord =
        ENGINE
            .authorization()
            .newAuthorization()
            .withOwnerKey(owner)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceKey("resource")
            .withResourceType("bpmn-id")
            .withPermissions(permissions)
            .create();

    // then
    final var createdAuthorization = createdAuthorizationRecord.getValue();
    Assertions.assertThat(createdAuthorization)
        .isNotNull()
        .hasFieldOrPropertyWithValue("ownerKey", owner)
        .hasFieldOrPropertyWithValue("ownerType", AuthorizationOwnerType.USER.toString())
        .hasFieldOrPropertyWithValue("resourceKey", "resource")
        .hasFieldOrPropertyWithValue("resourceType", "bpmn-id")
        .hasFieldOrPropertyWithValue("permissions", permissions);
  }

  @DisplayName(
      "should reject authorization create command when an authorization with owner and resource pair exists")
  @Test
  public void shouldNotDuplicate() {
    // given
    final var owner = "owner" + UUID.randomUUID();
    final var permissions = List.of("write:*");
    final var createdAuthorizationRecord =
        ENGINE
            .authorization()
            .newAuthorization()
            .withOwnerKey(owner)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceKey("resource")
            .withResourceType("bpmn-id")
            .withPermissions(permissions)
            .create();
    // when
    final var duplicatedAuthorizationRecord =
        ENGINE
            .authorization()
            .newAuthorization()
            .withOwnerKey(owner)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceKey("resource")
            .withResourceType("bpmn-id")
            .withPermissions(permissions)
            .expectRejection()
            .create();

    final var createdAuthorization = createdAuthorizationRecord.getValue();
    Assertions.assertThat(createdAuthorization)
        .isNotNull()
        .hasFieldOrPropertyWithValue("ownerKey", owner);

    assertThat(duplicatedAuthorizationRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create authorization with owner key: %s and resource key %s, but an authorization with these values already exists"
                .formatted(owner, "resource"));
  }
}
