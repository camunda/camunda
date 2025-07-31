/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
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
    engine
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
        .deploy(DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldBeAuthorizedToBroadcastSignalWithDefaultUser() {
    // given
    createProcessInstance();

    // when
    engine.signal().withSignalName(SIGNAL_NAME).broadcast(DEFAULT_USER.getUsername());

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
    final var user = createUser();
    addPermissionsToUser(
        user, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.CREATE_PROCESS_INSTANCE);
    addPermissionsToUser(
        user, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE_PROCESS_INSTANCE);

    // when
    engine.signal().withSignalName(SIGNAL_NAME).broadcast(user.getUsername());

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
    final var user = createUser();

    // when
    final var rejection =
        engine.signal().withSignalName(SIGNAL_NAME).expectRejection().broadcast(user.getUsername());

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
    final var user = createUser();
    addPermissionsToUser(
        user, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.CREATE_PROCESS_INSTANCE);

    // when
    final var rejection =
        engine.signal().withSignalName(SIGNAL_NAME).expectRejection().broadcast(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
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

  private long createProcessInstance() {
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());
  }
}
