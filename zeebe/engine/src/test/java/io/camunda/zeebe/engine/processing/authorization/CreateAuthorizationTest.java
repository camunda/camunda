/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class CreateAuthorizationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateAuthorization() {
    // when
    final var response =
        engine
            .authorization()
            .newAuthorization()
            // TODO: remove with https://github.com/camunda/camunda/issues/26883
            .withOwnerKey(1L)
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.DEPLOYMENT)
            .withPermissions(PermissionType.CREATE)
            .create()
            .getValue();

    // then
    assertThat(response)
        .extracting(
            AuthorizationRecordValue::getOwnerId,
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getResourceId,
            AuthorizationRecordValue::getResourceType,
            AuthorizationRecordValue::getAuthorizationPermissions)
        .containsExactly(
            "ownerId",
            AuthorizationOwnerType.USER,
            "resourceId",
            AuthorizationResourceType.DEPLOYMENT,
            Set.of(PermissionType.CREATE));
  }

  @Test
  @Ignore(
      "This test needs to be enabled once this feature: https://github.com/camunda/camunda/issues/27036 is implemented")
  public void shouldRejectIfPermissionAlreadyExistsDirectly() {
    // given
    engine
        .authorization()
        .newAuthorization()
        // TODO: remove with https://github.com/camunda/camunda/issues/26883
        .withOwnerKey(1L)
        .withOwnerId("ownerId")
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceId("resourceId")
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withPermissions(PermissionType.CREATE)
        .create();

    engine
        .authorization()
        .newAuthorization()
        // TODO: remove with https://github.com/camunda/camunda/issues/26883
        .withOwnerKey(1L)
        .withOwnerId("ownerId")
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceId("anotherResourceId")
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withPermissions(PermissionType.CREATE)
        .create();

    // when
    final var rejection =
        engine
            .authorization()
            .newAuthorization()
            // TODO: remove with https://github.com/camunda/camunda/issues/26883
            .withOwnerKey(1L)
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .withPermissions(PermissionType.CREATE)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Authorization already exists")
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create authorization for owner '%s' with permission type '%s' and resource type '%s', but this permission for resource identifiers '%s' already exist. Existing resource ids are: '%s'"
                .formatted(
                    "ownerId",
                    PermissionType.CREATE,
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    "resourceId",
                    "[resourceId, anotherResourceId]"));
  }

  @Test
  public void shouldRejectCreateUnsupportedPermission() {
    // given
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;

    // when
    final var rejection =
        engine
            .authorization()
            .newAuthorization()
            // TODO: remove with https://github.com/camunda/camunda/issues/26883
            .withOwnerKey(1L)
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceId("resourceId")
            .withResourceType(resourceType)
            .withPermissions(
                PermissionType.CREATE, PermissionType.DELETE_PROCESS, PermissionType.ACCESS)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create authorization with permission types '%s' and resource type '%s', but these permissions are not supported. Supported permission types are: '%s'"
                .formatted(
                    List.of(PermissionType.ACCESS),
                    resourceType,
                    resourceType.getSupportedPermissionTypes()));
  }
}
