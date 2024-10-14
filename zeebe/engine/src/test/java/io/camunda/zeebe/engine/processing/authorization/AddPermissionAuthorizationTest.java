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
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
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
            .withAction(PermissionAction.ADD)
            .withOwnerKey(ownerKey)
            .withResourceType(AuthorizationResourceType.DEPLOYMENT)
            .withPermission(PermissionType.CREATE, "foo")
            .withPermission(PermissionType.DELETE, "bar")
            .add()
            .getValue();

    // then
    assertThat(response)
        .extracting(
            AuthorizationRecordValue::getAction,
            AuthorizationRecordValue::getOwnerKey,
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getResourceType)
        .containsExactly(
            PermissionAction.ADD,
            ownerKey,
            AuthorizationOwnerType.USER,
            AuthorizationResourceType.DEPLOYMENT);
    assertThat(response.getPermissions())
        .extracting(PermissionValue::getPermissionType, PermissionValue::getResourceIds)
        .containsExactly(
            tuple(PermissionType.CREATE, List.of("foo")),
            tuple(PermissionType.DELETE, List.of("bar")));
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
            .withAction(PermissionAction.ADD)
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
}
