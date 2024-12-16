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
import org.junit.rules.TestWatcher;

public class AddPermissionAuthorizationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAddPermission() {
    // given no user
    final var ownerKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();

    // when
    final var response =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withResourceType(AuthorizationResourceType.DEPLOYMENT)
            .withPermission(PermissionType.CREATE, "foo")
            .withPermission(PermissionType.DELETE, "bar")
            .add()
            .getValue();

    // then
    assertThat(response)
        .extracting(
            AuthorizationRecordValue::getOwnerKey,
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getResourceType)
        .containsExactly(
            ownerKey, AuthorizationOwnerType.USER, AuthorizationResourceType.DEPLOYMENT);
    assertThat(response.getPermissions())
        .extracting(PermissionValue::getPermissionType, PermissionValue::getResourceIds)
        .containsExactly(
            tuple(PermissionType.CREATE, Set.of("foo")),
            tuple(PermissionType.DELETE, Set.of("bar")));
  }

  @Test
  public void shouldRejectIfNoOwnerExists() {
    // given no user
    final var ownerKey = 1L;

    // when
    final var rejection =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withResourceType(AuthorizationResourceType.DEPLOYMENT)
            .withPermission(PermissionType.CREATE, "foo")
            .expectRejection()
            .add();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Owner is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to find owner with key: '%d', but none was found".formatted(ownerKey));
  }

  @Test
  public void shouldRejectIfPermissionAlreadyExistsDirectly() {
    // given
    final var ownerKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    engine
        .authorization()
        .permission()
        .withOwnerKey(ownerKey)
        .withResourceType(AuthorizationResourceType.DEPLOYMENT)
        .withPermission(PermissionType.CREATE, "foo")
        .withPermission(PermissionType.DELETE, "bar", "baz")
        .add()
        .getValue();

    // when
    final var rejection =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withResourceType(AuthorizationResourceType.DEPLOYMENT)
            .withPermission(PermissionType.DELETE, "foo", "bar")
            .expectRejection()
            .add();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Permission already exists")
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add '%s' permission for resource '%s' and resource identifiers '%s' for owner '%s', but this permission for resource identifiers '%s' already exist. Existing resource ids are: '%s'"
                .formatted(
                    PermissionType.DELETE,
                    AuthorizationResourceType.DEPLOYMENT,
                    "[bar, foo]",
                    ownerKey,
                    "[bar]",
                    "[bar, baz]"));
  }

  @Test
  public void shouldNotRejectIfPermissionAlreadyExistsOnRole() {
    // given -- user is assigned a role that has the permission
    final var ownerKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var roleKey = engine.role().newRole("role").create().getKey();
    engine.role().addEntity(roleKey).withEntityKey(ownerKey).withEntityType(EntityType.USER).add();
    engine
        .authorization()
        .permission()
        .withOwnerKey(roleKey)
        .withResourceType(AuthorizationResourceType.DEPLOYMENT)
        .withPermission(PermissionType.CREATE, "foo")
        .withPermission(PermissionType.DELETE, "bar", "baz")
        .add()
        .getValue();

    // when -- assigning the permission directly to the user
    final var response =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withResourceType(AuthorizationResourceType.DEPLOYMENT)
            .withPermission(PermissionType.DELETE, "foo", "bar")
            .add();

    // then
    assertThat(response.getValue())
        .extracting(
            AuthorizationRecordValue::getOwnerKey,
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getResourceType)
        .containsExactly(
            ownerKey, AuthorizationOwnerType.USER, AuthorizationResourceType.DEPLOYMENT);
  }

  @Test
  public void shouldNotRejectIfPermissionAlreadyExistsOnGroup() {
    // given -- user is assigned a group that has the permission
    final var ownerKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var groupKey = engine.group().newGroup("group").create().getKey();
    engine
        .group()
        .addEntity(groupKey)
        .withEntityKey(ownerKey)
        .withEntityType(EntityType.USER)
        .add();
    engine
        .authorization()
        .permission()
        .withOwnerKey(groupKey)
        .withResourceType(AuthorizationResourceType.DEPLOYMENT)
        .withPermission(PermissionType.CREATE, "foo")
        .withPermission(PermissionType.DELETE, "bar", "baz")
        .add()
        .getValue();

    // when -- assigning the permission directly to the user
    final var response =
        engine
            .authorization()
            .permission()
            .withOwnerKey(ownerKey)
            .withResourceType(AuthorizationResourceType.DEPLOYMENT)
            .withPermission(PermissionType.DELETE, "foo", "bar")
            .add();

    // then
    assertThat(response.getValue())
        .extracting(
            AuthorizationRecordValue::getOwnerKey,
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getResourceType)
        .containsExactly(
            ownerKey, AuthorizationOwnerType.USER, AuthorizationResourceType.DEPLOYMENT);
  }
}
