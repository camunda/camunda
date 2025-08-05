/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public class ResourceFetchAuthorizationTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Test
  public void shouldBeAuthorizedToFetchResourceWithDefaultUser() {
    // given
    final var resourceMetadata = deployResource();
    final var resourceKey = resourceMetadata.getResourceKey();

    // when
    engine.resourceFetch().withResourceKey(resourceKey).fetch(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.resourceRecords()
                .withIntent(ResourceIntent.FETCHED)
                .withResourceKey(resourceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToFetchResourceWithPermissions() {
    // given
    final var resourceMetadata = deployResource();
    final var resourceKey = resourceMetadata.getResourceKey();
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.RESOURCE,
        PermissionType.READ,
        AuthorizationResourceMatcher.ID,
        resourceMetadata.getResourceId());

    // when
    engine.resourceFetch().withResourceKey(resourceKey).fetch(user.getUsername());

    // then
    assertThat(
            RecordingExporter.resourceRecords()
                .withIntent(ResourceIntent.FETCHED)
                .withResourceKey(resourceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToFetchResourceWithoutPermissions() {
    // given
    final var resourceMetadata = deployResource();
    final var resourceKey = resourceMetadata.getResourceKey();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .resourceFetch()
            .withResourceKey(resourceKey)
            .expectRejection()
            .fetch(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'READ' on resource 'RESOURCE', required resource identifiers are one of '[*, %s]'"
                .formatted(resourceMetadata.getResourceId()));
  }

  private ResourceMetadataValue deployResource() {
    final var deployment =
        engine.deployment().withJsonClasspathResource("/resource/test-rpa-1.rpa").deploy();
    return deployment.getValue().getResourceMetadata().getFirst();
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue();
  }

  private void addPermissionsToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final AuthorizationResourceMatcher matcher,
      final String resourceId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(matcher)
        .withResourceId(resourceId)
        .create(DEFAULT_USER.getUsername());
  }
}
