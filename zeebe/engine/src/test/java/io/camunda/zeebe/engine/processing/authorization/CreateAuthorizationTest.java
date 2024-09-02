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
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class CreateAuthorizationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @DisplayName(
      "should create an authorization if no authorization for owner and resource pair exists")
  @Test
  public void shouldCreateAuthorization() {
    // when
    final var ownerKey = 1L;
    final var permissions = List.of("write:*");
    final var createdAuthorizationRecord =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerKey(ownerKey)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType("bpmn-id")
            .withPermissions(permissions)
            .create();

    // then
    final var createdAuthorization = createdAuthorizationRecord.getValue();
    Assertions.assertThat(createdAuthorization)
        .isNotNull()
        .hasFieldOrPropertyWithValue("ownerKey", ownerKey)
        .hasFieldOrPropertyWithValue("ownerType", AuthorizationOwnerType.USER)
        .hasFieldOrPropertyWithValue("resourceKey", "resource")
        .hasFieldOrPropertyWithValue("resourceType", "bpmn-id")
        .hasFieldOrPropertyWithValue("permissions", permissions);
  }

  @DisplayName(
      "should reject authorization create command when an authorization with owner and resource pair exists")
  @Test
  public void shouldNotDuplicate() {
    // given
    final var ownerKey = 1L;
    final var permissions = List.of("write:*");
    final var createdAuthorizationRecord =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerKey(1L)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType("bpmn-id")
            .withPermissions(permissions)
            .create();
    // when
    final var duplicatedAuthorizationRecord =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerKey(ownerKey)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType("bpmn-id")
            .withPermissions(permissions)
            .expectRejection()
            .create();

    final var createdAuthorization = createdAuthorizationRecord.getValue();
    Assertions.assertThat(createdAuthorization)
        .isNotNull()
        .hasFieldOrPropertyWithValue("ownerKey", ownerKey);

    assertThat(duplicatedAuthorizationRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create authorization with owner key: %s and resource key %s, but an authorization with these values already exists"
                .formatted(ownerKey, "resource"));
  }
}
