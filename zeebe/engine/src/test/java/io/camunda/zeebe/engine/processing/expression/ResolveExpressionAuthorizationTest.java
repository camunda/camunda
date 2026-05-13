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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ExpressionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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

  @Test
  public void shouldBeAuthorizedWithProcessInstanceKeyWhenUserHasBothPermissions() {
    // given - a user with both EXPRESSION:EVALUATE and PROCESS_DEFINITION:READ_PROCESS_INSTANCE
    final var bpmnProcessId = "auth_pi_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.EXPRESSION,
        PermissionType.EVALUATE,
        AuthorizationResourceMatcher.ANY,
        "*");
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        bpmnProcessId);

    // when
    engine
        .expression()
        .withExpression(EXPRESSION)
        .withScopeKey(processInstanceKey)
        .resolve(user.getUsername());

    // then
    assertThat(
            RecordingExporter.expressionRecords()
                .withIntent(ExpressionIntent.EVALUATED)
                .withExpression(EXPRESSION)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedWithElementInstanceKeyWhenUserHasBothPermissions() {
    // given
    final var bpmnProcessId = "auth_ei_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.EXPRESSION,
        PermissionType.EVALUATE,
        AuthorizationResourceMatcher.ANY,
        "*");
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        bpmnProcessId);

    // when
    engine
        .expression()
        .withExpression(EXPRESSION)
        .withScopeKey(elementInstanceKey)
        .resolve(user.getUsername());

    // then
    assertThat(
            RecordingExporter.expressionRecords()
                .withIntent(ExpressionIntent.EVALUATED)
                .withExpression(EXPRESSION)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedWithProcessInstanceKeyWhenUserHasWildcardPermissions() {
    // given - wildcard EXPRESSION:EVALUATE and PROCESS_DEFINITION:READ_PROCESS_INSTANCE
    final var bpmnProcessId = "auth_wildcard_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.EXPRESSION,
        PermissionType.EVALUATE,
        AuthorizationResourceMatcher.ANY,
        "*");
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ANY,
        "*");

    // when
    engine
        .expression()
        .withExpression(EXPRESSION)
        .withScopeKey(processInstanceKey)
        .resolve(user.getUsername());

    // then
    assertThat(
            RecordingExporter.expressionRecords()
                .withIntent(ExpressionIntent.EVALUATED)
                .withExpression(EXPRESSION)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedWithProcessInstanceKeyIfNoPermissions() {
    // given
    final var bpmnProcessId = "auth_pi_no_perm_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);

    final var user = createUser();

    // when
    final var rejection =
        engine
            .expression()
            .withExpression(EXPRESSION)
            .withScopeKey(processInstanceKey)
            .expectRejection()
            .resolve(user.getUsername());

    // then - EXPRESSION:EVALUATE is checked first and the user has no permissions at all
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'EVALUATE' on resource 'EXPRESSION'");
  }

  @Test
  public void shouldBeUnauthorizedWithElementInstanceKeyIfNoPermissions() {
    // given
    final var bpmnProcessId = "auth_ei_no_perm_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    final var user = createUser();

    // when
    final var rejection =
        engine
            .expression()
            .withExpression(EXPRESSION)
            .withScopeKey(elementInstanceKey)
            .expectRejection()
            .resolve(user.getUsername());

    // then - EXPRESSION:EVALUATE is checked first and the user has no permissions at all
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'EVALUATE' on resource 'EXPRESSION'");
  }

  @Test
  public void shouldBeUnauthorizedWithProcessInstanceKeyIfOnlyEvaluateExpressionPermission() {
    // given - user has EXPRESSION:EVALUATE but not PROCESS_DEFINITION:READ_PROCESS_INSTANCE
    final var bpmnProcessId = "auth_pi_only_evaluate_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.EXPRESSION,
        PermissionType.EVALUATE,
        AuthorizationResourceMatcher.ANY,
        "*");

    // when
    final var rejection =
        engine
            .expression()
            .withExpression(EXPRESSION)
            .withScopeKey(processInstanceKey)
            .expectRejection()
            .resolve(user.getUsername());

    // then - scope is provided so EXPRESSION:EVALUATE is irrelevant; auth fails on
    // PROCESS_DEFINITION:READ_PROCESS_INSTANCE.
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'READ_PROCESS_INSTANCE' on resource"
                + " 'PROCESS_DEFINITION', required resource identifiers are one of '[*, "
                + bpmnProcessId
                + "]'");
  }

  @Test
  public void shouldBeUnauthorizedWithProcessInstanceKeyIfPermissionOnDifferentProcessId() {
    // given - user has EXPRESSION:EVALUATE and READ_PROCESS_INSTANCE on processB,
    // but the call targets processA so the second check must fail.
    final var processA = "auth_pi_proc_a_" + UUID.randomUUID();
    final var processB = "auth_pi_proc_b_" + UUID.randomUUID();
    deployProcess(processA);
    deployProcess(processB);
    final var processAInstanceKey = createProcessInstance(processA);

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.EXPRESSION,
        PermissionType.EVALUATE,
        AuthorizationResourceMatcher.ANY,
        "*");
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        processB);

    // when
    final var rejection =
        engine
            .expression()
            .withExpression(EXPRESSION)
            .withScopeKey(processAInstanceKey)
            .expectRejection()
            .resolve(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'READ_PROCESS_INSTANCE' on resource"
                + " 'PROCESS_DEFINITION', required resource identifiers are one of '[*, "
                + processA
                + "]'");
  }

  @Test
  public void shouldBeUnauthorizedWithElementInstanceKeyIfOnlyEvaluateExpressionPermission() {
    // given - user has EXPRESSION:EVALUATE but not PROCESS_DEFINITION:READ_PROCESS_INSTANCE,
    // so the second check fails for the resolved scope.
    final var bpmnProcessId = "auth_ei_only_evaluate_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.EXPRESSION,
        PermissionType.EVALUATE,
        AuthorizationResourceMatcher.ANY,
        "*");

    // when
    final var rejection =
        engine
            .expression()
            .withExpression(EXPRESSION)
            .withScopeKey(elementInstanceKey)
            .expectRejection()
            .resolve(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'READ_PROCESS_INSTANCE' on resource"
                + " 'PROCESS_DEFINITION', required resource identifiers are one of '[*, "
                + bpmnProcessId
                + "]'");
  }

  @Test
  public void shouldBeUnauthorizedWithProcessInstanceKeyIfOnlyReadProcessInstancePermission() {
    // given - user has PROCESS_DEFINITION:READ_PROCESS_INSTANCE but no EXPRESSION:EVALUATE,
    // so the first (always-required) check must fail.
    final var bpmnProcessId = "auth_pi_only_read_" + UUID.randomUUID();
    deployProcess(bpmnProcessId);
    final var processInstanceKey = createProcessInstance(bpmnProcessId);

    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.READ_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        bpmnProcessId);

    // when
    final var rejection =
        engine
            .expression()
            .withExpression(EXPRESSION)
            .withScopeKey(processInstanceKey)
            .expectRejection()
            .resolve(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'EVALUATE' on resource 'EXPRESSION'");
  }

  private void deployProcess(final String bpmnProcessId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(bpmnProcessId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  private long createProcessInstance(final String bpmnProcessId) {
    return engine
        .processInstance()
        .ofBpmnProcessId(bpmnProcessId)
        .create(DEFAULT_USER.getUsername());
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
