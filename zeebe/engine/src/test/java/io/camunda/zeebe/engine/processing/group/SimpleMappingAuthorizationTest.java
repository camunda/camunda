/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.OidcConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
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

public class SimpleMappingAuthorizationTest {

  public static final String USERNAME_CLAIM = "USERNAME_CLAIM";
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
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg -> {
                final var oidc = new OidcConfiguration();
                oidc.setEnabled(true);
                oidc.setUsername(USERNAME_CLAIM);
                cfg.getAuthorizations().setOidc(oidc);
              });

  private static long defaultUserKey = -1L;
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void beforeAll() {
    defaultUserKey =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(DEFAULT_USER.getUsername())
            .getFirst()
            .getKey();
  }

  @Test
  public void verifyPermissionOfSimpleMapping() {
    // given
    final var username = UUID.randomUUID().toString();
    ENGINE
        .authorization()
        .permission()
        .withOwnerId(username)
        .withResourceType(AuthorizationResourceType.DEPLOYMENT)
        .withPermission(PermissionType.CREATE, "*")
        .add(defaultUserKey);

    // when
    final var response =
        ENGINE
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess().startEvent().endEvent().done())
            .deploy(username, USERNAME_CLAIM);

    // then
    assertThat(
            RecordingExporter.processRecords(ProcessIntent.CREATED)
                .withBpmnProcessId(
                    response.getValue().getProcessesMetadata().getFirst().getBpmnProcessId())
                .exists())
        .isTrue();
  }

  @Test
  public void verifyNoPermissionOfSimpleMapping() {
    // given
    final var username = UUID.randomUUID().toString();

    // when
    final var rejection =
        ENGINE
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess().startEvent().endEvent().done())
            .expectRejection()
            .deploy(username, USERNAME_CLAIM);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE' on resource 'DEPLOYMENT'");
  }

  @Test
  public void verifyGroupPermissionOfSimpleMapping() {
    // given
    final var username = UUID.randomUUID().toString();
    final var groupId = UUID.randomUUID().toString();
    ENGINE.group().newGroup("group").withGroupId(groupId).create();
    ENGINE
        .authorization()
        .permission()
        .withOwnerId(groupId)
        .withResourceType(AuthorizationResourceType.DEPLOYMENT)
        .withPermission(PermissionType.CREATE, "*")
        .add(defaultUserKey);
    ENGINE.group().addEntity(groupId).withEntityId(username).add();

    // when
    final var response =
        ENGINE
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess().startEvent().endEvent().done())
            .deploy(username, USERNAME_CLAIM);

    // then
    assertThat(
            RecordingExporter.processRecords(ProcessIntent.CREATED)
                .withBpmnProcessId(
                    response.getValue().getProcessesMetadata().getFirst().getBpmnProcessId())
                .exists())
        .isTrue();
  }
}
