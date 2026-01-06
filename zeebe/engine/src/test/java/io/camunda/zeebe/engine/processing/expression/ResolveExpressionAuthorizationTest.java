/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterMode;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterTestWatcherMode;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public class ResolveExpressionAuthorizationTest {

  private static final String EXPRESSION = "=10 + 5";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withResetRecordingExporterTestWatcherMode(
              ResetRecordingExporterTestWatcherMode.BEFORE_ALL_TESTS_AND_AFTER_EACH_TEST)
          .withIdentitySetup(ResetRecordingExporterMode.NO_RESET_AFTER_IDENTITY_SETUP)
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Test
  public void shouldBeAuthorizedToEvaluateExpression() {
    // when
    engine.expression().withExpression(EXPRESSION).resolve(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.expressionRecords()
                .withIntent(ExpressionIntent.EVALUATED)
                .withExpression(EXPRESSION)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToEvaluateExpressionWithSpecificPermissions() {
    // given
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.EXPRESSION,
        PermissionType.EVALUATE,
        AuthorizationResourceMatcher.ANY,
        "*");

    // when
    engine.expression().withExpression(EXPRESSION).resolve(user.getUsername());

    // then
    assertThat(
            RecordingExporter.expressionRecords()
                .withIntent(ExpressionIntent.EVALUATED)
                .withExpression(EXPRESSION)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToEvaluateExpressionIfNoPermissions() {
    // given
    final var user = createUser();

    // when
    final var rejection =
        engine
            .expression()
            .withExpression(EXPRESSION)
            .expectRejection()
            .resolve(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'EVALUATE' on resource 'EXPRESSION'");
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
      final AuthorizationResourceType authorizationType,
      final PermissionType permissionType,
      final AuthorizationResourceMatcher matcher,
      final String resourceId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorizationType)
        .withResourceMatcher(matcher)
        .withResourceId(resourceId)
        .create();
  }
}
