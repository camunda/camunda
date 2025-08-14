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

public class UpdateAuthorizationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldUpdateAuthorization() {
    // given
    final var authorizationKey =
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
            .getValue()
            .getAuthorizationKey();

    // when
    final var updatedAuthorization =
        engine
            .authorization()
            .updateAuthorization(authorizationKey)
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.GROUP)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.CREATE)
            .update()
            .getValue();

    // then
    assertThat(updatedAuthorization)
        .extracting(
            AuthorizationRecordValue::getAuthorizationKey,
            AuthorizationRecordValue::getOwnerId,
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getResourceId,
            AuthorizationRecordValue::getResourceType,
            AuthorizationRecordValue::getPermissionTypes)
        .containsExactly(
            authorizationKey,
            "ownerId",
            AuthorizationOwnerType.GROUP,
            "resourceId",
            AuthorizationResourceType.RESOURCE,
            Set.of(PermissionType.CREATE));
  }

  @Test
  public void shouldRejectTheCommandIfAuthorizationDoesNotExist() {
    final var userNotFoundRejection =
        engine
            .authorization()
            .updateAuthorization(1L)
            .withResourceMatcher(AuthorizationResourceMatcher.UNSPECIFIED)
            .expectRejection()
            .update();

    Assertions.assertThat(userNotFoundRejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update authorization with key 1, but an authorization with this key does not exist");
  }

  @Test
  public void shouldRejectUpdateUnsupportedPermission() {
    // given
    final var resourceType = AuthorizationResourceType.RESOURCE;

    // when
    final var authorizationKey =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(resourceType)
            .withPermissions(PermissionType.CREATE)
            .create()
            .getValue()
            .getAuthorizationKey();

    final var rejection =
        engine
            .authorization()
            .updateAuthorization(authorizationKey)
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.GROUP)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.ACCESS)
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to update authorization with permission types '%s' and resource type '%s', but these permissions are not supported. Supported permission types are: '%s'"
                .formatted(
                    List.of(PermissionType.ACCESS),
                    resourceType,
                    resourceType.getSupportedPermissionTypes()));
  }

  @Test
  public void shouldRejectUpdateWhenMappingDoesNotExist() {
    // given
    final var authorizationKey =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId("existing-owner-id")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resource-id")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.CREATE)
            .create()
            .getValue()
            .getAuthorizationKey();

    final var nonexistentMappingRuleId = "nonexistent-mapping-rule-id";

    // when
    final var rejection =
        engine
            .authorization()
            .updateAuthorization(authorizationKey)
            .withOwnerId(nonexistentMappingRuleId)
            .withOwnerType(AuthorizationOwnerType.MAPPING_RULE)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resource-id")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.CREATE)
            .expectRejection()
            .update();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to create or update authorization with ownerId '%s', but a mapping rule with this ID does not exist."
                .formatted(nonexistentMappingRuleId));
  }
}
