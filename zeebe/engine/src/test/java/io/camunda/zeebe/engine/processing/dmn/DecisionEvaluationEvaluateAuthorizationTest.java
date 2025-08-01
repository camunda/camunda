/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.dmn;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class DecisionEvaluationEvaluateAuthorizationTest {
  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());
  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";
  private static final String DECISION_ID = "jedi_or_sith";

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

  @Before
  public void before() {
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy(DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldBeAuthorizedToEvaluateDecisionWithDefaultUser() {
    // when
    final var response =
        engine
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .evaluate(DEFAULT_USER.getUsername());

    // then
    assertThat(response.getValue().getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  @Test
  public void shouldBeAuthorizedToEvaluateDecisionWithUser() {
    // given
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.DECISION_DEFINITION,
        PermissionType.CREATE_DECISION_INSTANCE);

    // when
    final var response =
        engine
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .evaluate(user.getUsername());

    // then
    assertThat(response.getValue().getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  @Test
  public void shouldBeUnauthorizedToEvaluateDecisionIfNoPermissions() {
    // given
    final var user = createUser();

    // when
    final var rejection =
        engine
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .expectRejection()
            .evaluate(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(DECISION_ID));
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
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withPermissions(permissionType)
        .withResourceMatcher(AuthorizationResourceMatcher.ANY)
        .withResourceId("*")
        .create(DEFAULT_USER.getUsername());
  }
}
