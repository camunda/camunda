/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
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

public class MessageCorrelationCorrelateAuthorizationTest {
  public static final String INTERMEDIATE_MSG_NAME = "intermediateMsg";
  public static final String START_MSG_NAME = "startMsg";
  public static final String CORRELATION_KEY_VARIABLE = "correlationKey";
  private static final String PROCESS_ID = "processId";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withoutAwaitingIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)));

  private static long defaultUserKey = -1L;
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void beforeAll() {
    defaultUserKey =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(DEFAULT_USER.getUsername())
            .getFirst()
            .getKey();
    ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent()
                .message(
                    m ->
                        m.name(INTERMEDIATE_MSG_NAME)
                            .zeebeCorrelationKeyExpression(CORRELATION_KEY_VARIABLE))
                .endEvent()
                .moveToProcess(PROCESS_ID)
                .startEvent()
                .message(m -> m.name(START_MSG_NAME))
                .done())
        .deploy(defaultUserKey);
  }

  @Test
  public void shouldBeAuthorizedToCorrelateMessageToIntermediateEventWithDefaultUser() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);

    // when
    ENGINE
        .messageCorrelation()
        .withName(INTERMEDIATE_MSG_NAME)
        .withCorrelationKey(correlationKey)
        .correlate(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
                .withName(INTERMEDIATE_MSG_NAME)
                .withCorrelationKey(correlationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCorrelateMessageToIntermediateEventWithUser() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE);

    // when
    ENGINE
        .messageCorrelation()
        .withName(INTERMEDIATE_MSG_NAME)
        .withCorrelationKey(correlationKey)
        .correlate(userKey);

    // then
    assertThat(
            RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
                .withName(INTERMEDIATE_MSG_NAME)
                .withCorrelationKey(correlationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToCorrelateMessageToIntermediateEventIfNoPermissions() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);
    final var userKey = createUser();

    // when
    final var rejection =
        ENGINE
            .messageCorrelation()
            .withName(INTERMEDIATE_MSG_NAME)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .correlate(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldBeAuthorizedToCorrelateMessageToStartEventWithDefaultUser() {
    // when
    ENGINE
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey("")
        .correlate(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
                .withName(START_MSG_NAME)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCorrelateMessageToStartEventWithUser() {
    // given
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        PROCESS_ID);

    // when
    ENGINE.messageCorrelation().withName(START_MSG_NAME).withCorrelationKey("").correlate(userKey);

    // then
    assertThat(
            RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
                .withName(START_MSG_NAME)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToCorrelateMessageToStartEventIfNoPermissions() {
    // given
    final var userKey = createUser();

    // when
    final var rejection =
        ENGINE
            .messageCorrelation()
            .withName(START_MSG_NAME)
            .withCorrelationKey("")
            .expectRejection()
            .correlate(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldNotCorrelateAnyMessageIfUnauthorizedForOne() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        PROCESS_ID);
    final var unauthorizedProcessId = "unauthorizedProcessId";
    final var resourceName = "unauthorizedProcess.xml";
    ENGINE
        .deployment()
        .withXmlResource(
            resourceName,
            Bpmn.createExecutableProcess(unauthorizedProcessId)
                .startEvent()
                .message(m -> m.name(START_MSG_NAME))
                .endEvent()
                .done())
        .deploy(defaultUserKey)
        .getKey();

    // when
    final var rejection =
        ENGINE
            .messageCorrelation()
            .withName(START_MSG_NAME)
            .withCorrelationKey("")
            .expectRejection()
            .correlate(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(unauthorizedProcessId));
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
    addPermissionsToUser(userKey, authorization, permissionType, "*");
  }

  private void addPermissionsToUser(
      final long userKey,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final String... resourceIds) {
    ENGINE
        .authorization()
        .permission()
        .withOwnerKey(userKey)
        .withResourceType(authorization)
        .withPermission(permissionType, resourceIds)
        .add(defaultUserKey);
  }

  private void createProcessInstance(final String correlationKey) {
    ENGINE
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariable(CORRELATION_KEY_VARIABLE, correlationKey)
        .create(defaultUserKey);
  }
}