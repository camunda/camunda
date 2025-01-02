/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
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

public class SignalBroadcastAuthorizationTest {
  public static final String SIGNAL_NAME = "signal";
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
                .signal(s -> s.name(SIGNAL_NAME))
                .endEvent()
                .moveToProcess(PROCESS_ID)
                .startEvent()
                .signal(s -> s.name(SIGNAL_NAME))
                .done())
        .deploy(defaultUserKey);
  }

  @Test
  public void shouldBeAuthorizedToBroadcastSignalWithDefaultUser() {
    // given
    createProcessInstance();

    // when
    ENGINE.signal().withSignalName(SIGNAL_NAME).broadcast(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.signalRecords(SignalIntent.BROADCASTED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToBroadcastSignalWithUser() {
    // given
    createProcessInstance();
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE);
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE);

    // when
    ENGINE.signal().withSignalName(SIGNAL_NAME).broadcast(userKey);

    // then
    assertThat(
            RecordingExporter.signalRecords(SignalIntent.BROADCASTED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToBroadcastSignalIfNoPermissions() {
    // given
    createProcessInstance();
    final var userKey = createUser();

    // when
    final var rejection =
        ENGINE.signal().withSignalName(SIGNAL_NAME).expectRejection().broadcast(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldNotBroadcastSignalIfUnauthorizedForOne() {
    // given
    createProcessInstance();
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE);

    // when
    final var rejection =
        ENGINE.signal().withSignalName(SIGNAL_NAME).expectRejection().broadcast(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
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

  private long createProcessInstance() {
    return ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create(defaultUserKey);
  }
}
