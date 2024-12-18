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
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
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

public class MessagePublishAuthorizationTest {
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
  public void shouldBeAuthorizedToPublishMessageWithDefaultUser() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);

    // when
    ENGINE
        .message()
        .withName(INTERMEDIATE_MSG_NAME)
        .withCorrelationKey(correlationKey)
        .publish(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                .withName(INTERMEDIATE_MSG_NAME)
                .withCorrelationKey(correlationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToPublishMessageWithUser() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);
    final var userKey = createUser();
    addPermissionsToUser(userKey, AuthorizationResourceType.MESSAGE, PermissionType.CREATE);

    // when
    ENGINE
        .message()
        .withName(INTERMEDIATE_MSG_NAME)
        .withCorrelationKey(correlationKey)
        .publish(userKey);

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                .withName(INTERMEDIATE_MSG_NAME)
                .withCorrelationKey(correlationKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToPublishMessageIfNoPermissions() {
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);
    final var userKey = createUser();

    // when
    final var rejection =
        ENGINE
            .message()
            .withName(INTERMEDIATE_MSG_NAME)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .publish(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE' on resource 'MESSAGE'");
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

  private void createProcessInstance(final String correlationKey) {
    ENGINE
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariable(CORRELATION_KEY_VARIABLE, correlationKey)
        .create(defaultUserKey);
  }
}
