/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)));

  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";
  private static final String DECISION_ID = "jedi_or_sith";

  private static long defaultUserKey = -1L;
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void beforeAll() {
    defaultUserKey =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(DEFAULT_USER.getUsername())
            .getFirst()
            .getKey();
    ENGINE.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy(defaultUserKey);
  }

  @Test
  public void shouldBeAuthorizedToEvaluateDecisionWithDefaultUser() {
    // when
    final var response =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .evaluate(defaultUserKey);

    // then
    assertThat(response.getValue().getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  @Test
  public void shouldBeAuthorizedToEvaluateDecisionWithUser() {
    // given
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.DECISION_DEFINITION,
        PermissionType.CREATE_DECISION_INSTANCE);

    // when
    final var response =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .evaluate(userKey);

    // then
    assertThat(response.getValue().getDecisionOutput()).isEqualTo("\"Sith\"");
  }

  @Test
  public void shouldBeUnauthorizedToEvaluateDecisionIfNoPermissions() {
    // given
    final var userKey = createUser();

    // when
    final var rejection =
        ENGINE
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "red")
            .expectRejection()
            .evaluate(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(DECISION_ID));
  }

  private static long createUser() {
    return ENGINE
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getKey();
  }

  private void addPermissionsToUser(
      final long userKey,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType) {
    ENGINE
        .authorization()
        .permission()
        .withOwnerKey(userKey)
        .withResourceType(authorization)
        .withPermission(permissionType, "*")
        .add(defaultUserKey);
  }
}
