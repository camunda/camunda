/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ResourceDeletionAuthorizationTest {
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
  }

  @Test
  public void shouldBeAuthorizedToDeleteProcessDefinitionWithDefaultUser() {
    // given
    final var processDefinitionKey = deployProcessDefinition(Strings.newRandomValidBpmnId());

    // when
    ENGINE.resourceDeletion().withResourceKey(processDefinitionKey).delete(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(processDefinitionKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToDeleteProcessDefinitionWithPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var processDefinitionKey = deployProcessDefinition(processId);
    final var userKey = createUser();
    addPermissionsToUser(
        userKey, AuthorizationResourceType.DEPLOYMENT, PermissionType.DELETE_PROCESS, processId);

    // when
    ENGINE.resourceDeletion().withResourceKey(processDefinitionKey).delete(userKey);

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(processDefinitionKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnAuthorizedToDeleteProcessDefinitionWithPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var processDefinitionKey = deployProcessDefinition(processId);
    final var userKey = createUser();

    // when
    ENGINE
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .expectRejection()
        .delete(userKey);

    // then
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE_PROCESS' on resource 'DEPLOYMENT', required resource identifiers are one of '[*, %s]'"
                .formatted(processId));
  }

  @Test
  public void shouldBeAuthorizedToDeleteDrdWithDefaultUser() {
    // given
    final var drdKey = deployDrd();

    // when
    ENGINE.resourceDeletion().withResourceKey(drdKey).delete(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(drdKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToDeleteDrdWithPermissions() {
    // given
    final var drdId = "force_users";
    final var drdKey = deployDrd();
    final var userKey = createUser();
    addPermissionsToUser(
        userKey, AuthorizationResourceType.DEPLOYMENT, PermissionType.DELETE_DRD, drdId);

    // when
    ENGINE.resourceDeletion().withResourceKey(drdKey).delete(userKey);

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(drdKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnAuthorizedToDeleteDrdWithPermissions() {
    // given
    final var drdId = "force_users";
    final var drdKey = deployDrd();
    final var userKey = createUser();

    // when
    ENGINE.resourceDeletion().withResourceKey(drdKey).expectRejection().delete(userKey);

    // then
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE_DRD' on resource 'DEPLOYMENT', required resource identifiers are one of '[*, %s]'"
                .formatted(drdId));
  }

  @Test
  public void shouldBeAuthorizedToDeleteFormWithDefaultUser() {
    // given
    final var formKey = deployForm();

    // when
    ENGINE.resourceDeletion().withResourceKey(formKey).delete(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(formKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToDeleteFormWithPermissions() {
    // given
    final var formId = "Form_0w7r08e";
    final var formKey = deployForm();
    final var userKey = createUser();
    addPermissionsToUser(
        userKey, AuthorizationResourceType.DEPLOYMENT, PermissionType.DELETE_FORM, formId);

    // when
    ENGINE.resourceDeletion().withResourceKey(formKey).delete(userKey);

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(formKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnAuthorizedToDeleteFormWithPermissions() {
    // given
    final var formId = "Form_0w7r08e";
    final var formKey = deployForm();
    final var userKey = createUser();

    // when
    ENGINE.resourceDeletion().withResourceKey(formKey).expectRejection().delete(userKey);

    // then
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE_FORM' on resource 'DEPLOYMENT', required resource identifiers are one of '[*, %s]'"
                .formatted(formId));
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

  private long deployProcessDefinition(final String processId) {
    return ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy(defaultUserKey)
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private long deployDrd() {
    return ENGINE
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .deploy(defaultUserKey)
        .getValue()
        .getDecisionRequirementsMetadata()
        .getFirst()
        .getDecisionRequirementsKey();
  }

  private long deployForm() {
    return ENGINE
        .deployment()
        .withXmlClasspathResource("/form/test-form-1.form")
        .deploy(defaultUserKey)
        .getValue()
        .getFormMetadata()
        .getFirst()
        .getFormKey();
  }
}
