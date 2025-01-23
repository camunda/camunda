/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue.PermissionValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.rules.TestWatcher;

public class RemovePermissionAuthorizationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRemovePermission() {
    // given
    final var owner =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var ownerKey = owner.getKey();
    final var ownerId = owner.getValue().getUsername();

    engine
        .authorization()
        .permission()
        .withOwnerKey(ownerKey)
        .withOwnerId(ownerId)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermission(PermissionType.CREATE, "foo")
        .withPermission(PermissionType.DELETE_PROCESS, "bar")
        .add()
        .getValue();

    // when
    final var response =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withOwnerId(ownerId)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermission(PermissionType.CREATE, "foo")
            .withPermission(PermissionType.DELETE_PROCESS, "bar")
            .remove()
            .getValue();

    // then
    assertThat(response)
        .extracting(
            AuthorizationRecordValue::getOwnerKey,
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getResourceType)
        .containsExactly(ownerKey, AuthorizationOwnerType.USER, AuthorizationResourceType.RESOURCE);
    assertThat(response.getPermissions())
        .extracting(PermissionValue::getPermissionType, PermissionValue::getResourceIds)
        .containsExactly(
            tuple(PermissionType.CREATE, Set.of("foo")),
            tuple(PermissionType.DELETE_PROCESS, Set.of("bar")));
  }

  // TODO: we should decide if we refactor or remove this test with the GitHub issue specified below
  @Disabled("https://github.com/camunda/camunda/issues/27344")
  @Test
  public void shouldRejectIfNoOwnerExists() {
    // given no user
    final var ownerKey = 1L;
    final var ownerId = "bar";

    // when
    final var rejection =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withOwnerId(ownerId)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermission(PermissionType.CREATE, "foo")
            .expectRejection()
            .remove();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Owner is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to find owner with key: '%d', but none was found".formatted(ownerKey));
  }

  @Test
  public void shouldRejectIfPermissionDoesNotExist() {
    // given
    final var owner =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var ownerKey = owner.getKey();
    final var ownerId = owner.getValue().getUsername();

    engine
        .authorization()
        .permission()
        .withOwnerKey(ownerKey)
        .withOwnerId(ownerId)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermission(PermissionType.CREATE, "foo")
        .withPermission(PermissionType.DELETE_PROCESS, "bar")
        .add()
        .getValue();

    // when
    final var rejection =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withOwnerId(ownerId)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermission(PermissionType.DELETE_PROCESS, "foo", "bar")
            .expectRejection()
            .remove();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Permission is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove '%s' permission for resource '%s' and resource identifiers '%s' for owner '%s', but this permission for resource identifiers '%s' is not found. Existing resource ids are: '%s'"
                .formatted(
                    PermissionType.DELETE_PROCESS,
                    AuthorizationResourceType.RESOURCE,
                    "[bar, foo]",
                    ownerKey,
                    "[foo]",
                    "[bar]"));
  }

  @Test
  public void shouldRejectIfPermissionIsOnlyInheritedFromRole() {
    // given
    final var user =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var userKey = user.getKey();
    final var userId = user.getValue().getUsername();
    final var roleKey = engine.role().newRole("role").create().getKey();
    final var roleId = String.valueOf(roleKey);

    engine
        .authorization()
        .permission()
        .withOwnerKey(roleKey)
        .withOwnerId(roleId)
        .withOwnerType(AuthorizationOwnerType.ROLE)
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermission(PermissionType.CREATE, "foo")
        .withPermission(PermissionType.DELETE_PROCESS, "bar")
        .add()
        .getValue();
    engine.role().addEntity(roleKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var rejection =
        engine
            .authorization()
            .permission()
            .withOwnerKey(userKey)
            .withOwnerId(userId)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermission(PermissionType.DELETE_PROCESS, "foo", "bar")
            .expectRejection()
            .remove();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Permission is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove '%s' permission for resource '%s' and resource identifiers '%s' for owner '%s', but this permission for resource identifiers '%s' is not found. Existing resource ids are: '%s'"
                .formatted(
                    PermissionType.DELETE_PROCESS,
                    AuthorizationResourceType.RESOURCE,
                    "[bar, foo]",
                    userKey,
                    "[bar, foo]",
                    "[]"));
  }

  @Test
  public void shouldRejectIfPermissionIsOnlyInheritedFromGroup() {
    // given
    final var user =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var userKey = user.getKey();
    final var userId = user.getValue().getUsername();
    final var groupKey = engine.group().newGroup("role").create().getKey();
    final var groupId = String.valueOf(groupKey);

    engine
        .authorization()
        .permission()
        .withOwnerKey(groupKey)
        .withOwnerId(groupId)
        .withOwnerType(AuthorizationOwnerType.GROUP)
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermission(PermissionType.CREATE, "foo")
        .withPermission(PermissionType.DELETE_PROCESS, "bar")
        .add()
        .getValue();
    engine.group().addEntity(groupKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var rejection =
        engine
            .authorization()
            .permission()
            .withOwnerKey(userKey)
            .withOwnerId(userId)
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermission(PermissionType.DELETE_PROCESS, "foo", "bar")
            .expectRejection()
            .remove();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Permission is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove '%s' permission for resource '%s' and resource identifiers '%s' for owner '%s', but this permission for resource identifiers '%s' is not found. Existing resource ids are: '%s'"
                .formatted(
                    PermissionType.DELETE_PROCESS,
                    AuthorizationResourceType.RESOURCE,
                    "[bar, foo]",
                    userKey,
                    "[bar, foo]",
                    "[]"));
  }
}
