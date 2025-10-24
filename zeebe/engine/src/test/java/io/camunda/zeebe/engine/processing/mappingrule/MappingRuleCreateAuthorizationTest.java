/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.mappingrule;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MappingRuleCreateAuthorizationTest {
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

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedToCreateMappingRuleWithDefaultUser() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();

    // when
    engine
        .mappingRule()
        .newMappingRule(mappingRuleId)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.mappingRuleRecords(MappingRuleIntent.CREATED)
                .withMappingRuleId(mappingRuleId)
                .withClaimName(claimName)
                .withClaimValue(claimValue)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCreateMappingRuleWithPermissions() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var user = createUser();
    addPermissionsToUser(user, AuthorizationResourceType.MAPPING_RULE, PermissionType.CREATE);

    // when
    engine
        .mappingRule()
        .newMappingRule(mappingRuleId)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create(user.getUsername());

    // then
    assertThat(
            RecordingExporter.mappingRuleRecords(MappingRuleIntent.CREATED)
                .withMappingRuleId(mappingRuleId)
                .withClaimName(claimName)
                .withClaimValue(claimValue)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnAuthorizedToCreateMappingRuleWithoutPermissions() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .mappingRule()
            .newMappingRule(mappingRuleId)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .expectRejection()
            .create(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE' on resource 'MAPPING_RULE'");
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
      final PermissionType permissionType) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
  }
}
