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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Set;
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
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.RESOURCE)
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
            AuthorizationRecordValue::getPermissionTypes)
        .containsExactly(
            "ownerId",
            AuthorizationOwnerType.USER,
            "resourceId",
            AuthorizationResourceType.RESOURCE,
            Set.of(PermissionType.CREATE));
  }

  @Test
  public void shouldRejectIfPermissionAlreadyExistsDirectly() {
    // given
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId("ownerId")
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId("resourceId")
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermissions(PermissionType.CREATE)
        .create();

    engine
        .authorization()
        .newAuthorization()
        .withOwnerId("ownerId")
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId("anotherResourceId")
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermissions(PermissionType.CREATE)
        .create();

    // when
    final var rejection =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.CREATE)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Authorization already exists")
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create authorization for owner '%s' for resource identifier '%s', but an authorization for this resource identifier already exists."
                .formatted("ownerId", "resourceId"));
  }

  @Test
  public void shouldRejectCreateUnsupportedPermission() {
    // given
    final var resourceType = AuthorizationResourceType.RESOURCE;

    // when
    final var rejection =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
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

  @Test
  public void shouldRejectCreateWhenMappingDoesNotExist() {
    // given
    final var nonexistentMappingId = "nonexistent-mapping-id";

    // when
    final var rejection =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId(nonexistentMappingId)
            .withOwnerType(AuthorizationOwnerType.MAPPING_RULE)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.CREATE)
            .expectRejection()
            .create();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to create or update authorization with ownerId '%s', but a mapping rule with this ID does not exist."
                .formatted(nonexistentMappingId));
  }
}
