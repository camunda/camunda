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
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  @Test
  public void shouldBeAuthorizedToDeleteProcessDefinitionWithDefaultUser() {
    // given
    final var processDefinitionKey = deployProcessDefinition(Strings.newRandomValidBpmnId());

    // when
    engine
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .delete(DEFAULT_USER.getUsername());

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
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.RESOURCE,
        PermissionType.DELETE_PROCESS,
        AuthorizationResourceMatcher.ID,
        processId);

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete(user.getUsername());

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(processDefinitionKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeleteProcessDefinitionWithoutPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var processDefinitionKey = deployProcessDefinition(processId);
    final var user = createUser();

    // when
    engine
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .expectRejection()
        .delete(user.getUsername());

    // then
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE_PROCESS' on resource 'RESOURCE', required resource identifiers are one of '[*, %s]'"
                .formatted(processId));
  }

  @Test
  public void shouldBeAuthorizedToDeleteDrdWithDefaultUser() {
    // given
    final var drdKey = deployDrd();

    // when
    engine.resourceDeletion().withResourceKey(drdKey).delete(DEFAULT_USER.getUsername());

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
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.RESOURCE,
        PermissionType.DELETE_DRD,
        AuthorizationResourceMatcher.ID,
        drdId);

    // when
    engine.resourceDeletion().withResourceKey(drdKey).delete(user.getUsername());

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(drdKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeleteDrdWithoutPermissions() {
    // given
    final var drdId = "force_users";
    final var drdKey = deployDrd();
    final var user = createUser();

    // when
    engine.resourceDeletion().withResourceKey(drdKey).expectRejection().delete(user.getUsername());

    // then
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE_DRD' on resource 'RESOURCE', required resource identifiers are one of '[*, %s]'"
                .formatted(drdId));
  }

  @Test
  public void shouldBeAuthorizedToDeleteFormWithDefaultUser() {
    // given
    final var formKey = deployForm();

    // when
    engine.resourceDeletion().withResourceKey(formKey).delete(DEFAULT_USER.getUsername());

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
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.RESOURCE,
        PermissionType.DELETE_FORM,
        AuthorizationResourceMatcher.ID,
        formId);

    // when
    engine.resourceDeletion().withResourceKey(formKey).delete(user.getUsername());

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(formKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeleteFormWithoutPermissions() {
    // given
    final var formId = "Form_0w7r08e";
    final var formKey = deployForm();
    final var user = createUser();

    // when
    engine.resourceDeletion().withResourceKey(formKey).expectRejection().delete(user.getUsername());

    // then
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE_FORM' on resource 'RESOURCE', required resource identifiers are one of '[*, %s]'"
                .formatted(formId));
  }

  @Test
  public void shouldBeAuthorizedToDeleteResourceWithDefaultUser() {
    // given
    final var resourceKey = deployResource();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(resourceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToDeleteResourceWithPermissions() {
    // given
    final var resourceId = "Rpa_0w7r08e";
    final var resourceKey = deployResource();
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.RESOURCE,
        PermissionType.DELETE_RESOURCE,
        AuthorizationResourceMatcher.ID,
        resourceId);

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete(user.getUsername());

    // then
    assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
                .withResourceKey(resourceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeleteResourceWithoutPermissions() {
    // given
    final var resourceId = "Rpa_0w7r08e";
    final var resourceKey = deployResource();
    final var user = createUser();

    // when
    engine
        .resourceDeletion()
        .withResourceKey(resourceKey)
        .expectRejection()
        .delete(user.getUsername());

    // then
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE_RESOURCE' on resource 'RESOURCE', required resource identifiers are one of '[*, %s]'"
                .formatted(resourceId));
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
      final PermissionType permissionType,
      final AuthorizationResourceMatcher matcher,
      final String resourceId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(matcher)
        .withResourceId(resourceId)
        .create(DEFAULT_USER.getUsername());
  }

  private long deployProcessDefinition(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy(DEFAULT_USER.getUsername())
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private long deployDrd() {
    return engine
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .deploy(DEFAULT_USER.getUsername())
        .getValue()
        .getDecisionRequirementsMetadata()
        .getFirst()
        .getDecisionRequirementsKey();
  }

  private long deployForm() {
    return engine
        .deployment()
        .withXmlClasspathResource("/form/test-form-1.form")
        .deploy(DEFAULT_USER.getUsername())
        .getValue()
        .getFormMetadata()
        .getFirst()
        .getFormKey();
  }

  private long deployResource() {
    return engine
        .deployment()
        .withJsonClasspathResource("/resource/test-rpa-1.rpa")
        .deploy(DEFAULT_USER.getUsername())
        .getValue()
        .getResourceMetadata()
        .getFirst()
        .getResourceKey();
  }
}
