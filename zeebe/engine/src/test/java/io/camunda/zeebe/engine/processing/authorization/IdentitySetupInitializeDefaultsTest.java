/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.identity.authorization.property.UserTaskAuthorizationProperties;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterMode;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterTestWatcherMode;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher.ResetMode;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class IdentitySetupInitializeDefaultsTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withResetRecordingExporterTestWatcherMode(
              ResetRecordingExporterTestWatcherMode.ONLY_BEFORE_AND_AFTER_ALL_TESTS)
          .withIdentitySetup(ResetRecordingExporterMode.NO_RESET_AFTER_IDENTITY_SETUP);

  @Rule
  public final TestWatcher testWatcher =
      new RecordingExporterTestWatcher().withResetMode(ResetMode.NEVER);

  @Test
  public void shouldCreateAdminRoleByDefault() {
    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .authorizationRecords()
                .withOwnerId(DefaultRole.ADMIN.getId()))
        .extracting(Record::getValue)
        .describedAs("Expect all admin role authorizations to be owned by the admin role")
        .allSatisfy(
            auth ->
                Assertions.assertThat(auth)
                    .hasOwnerId(DefaultRole.ADMIN.getId())
                    .hasOwnerType(AuthorizationOwnerType.ROLE))
        .describedAs("Expect all admin role authorizations to have the wildcard resource ID")
        .allSatisfy(auth -> Assertions.assertThat(auth).hasResourceId(WILDCARD.getResourceId()))
        .describedAs("Expect the admin role authorizations to have specific resource permissions")
        .satisfiesExactlyInAnyOrder(
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.AUTHORIZATION)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.MAPPING_RULE)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.MESSAGE)
                    .hasOnlyPermissionTypes(PermissionType.CREATE, PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.COMPONENT)
                    .hasOnlyPermissionTypes(PermissionType.ACCESS),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.SYSTEM)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ,
                        PermissionType.READ_USAGE_METRIC,
                        PermissionType.READ_JOB_METRIC,
                        PermissionType.UPDATE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.TENANT)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.RESOURCE)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ,
                        PermissionType.CREATE,
                        PermissionType.DELETE_FORM,
                        PermissionType.DELETE_PROCESS,
                        PermissionType.DELETE_DRD,
                        PermissionType.DELETE_RESOURCE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.READ_PROCESS_INSTANCE,
                        PermissionType.READ_USER_TASK,
                        PermissionType.UPDATE_PROCESS_INSTANCE,
                        PermissionType.UPDATE_USER_TASK,
                        PermissionType.CREATE_PROCESS_INSTANCE,
                        PermissionType.CANCEL_PROCESS_INSTANCE,
                        PermissionType.MODIFY_PROCESS_INSTANCE,
                        PermissionType.DELETE_PROCESS_INSTANCE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DECISION_DEFINITION)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ_DECISION_DEFINITION,
                        PermissionType.READ_DECISION_INSTANCE,
                        PermissionType.CREATE_DECISION_INSTANCE,
                        PermissionType.DELETE_DECISION_INSTANCE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.GROUP)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.USER)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.USER_TASK)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.CLAIM,
                        PermissionType.COMPLETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.ROLE)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.CLUSTER_VARIABLE)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.READ,
                        PermissionType.UPDATE,
                        PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.BATCH)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE,
                        PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE,
                        PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE,
                        PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE,
                        PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE,
                        PermissionType.CREATE_BATCH_OPERATION_RESOLVE_INCIDENT,
                        PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE,
                        PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION,
                        PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION,
                        PermissionType.UPDATE,
                        PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DOCUMENT)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.EXPRESSION)
                    .hasOnlyPermissionTypes(PermissionType.EVALUATE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.AUDIT_LOG)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.GLOBAL_LISTENER)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE_TASK_LISTENER,
                        PermissionType.READ_TASK_LISTENER,
                        PermissionType.UPDATE_TASK_LISTENER,
                        PermissionType.DELETE_TASK_LISTENER));
  }

  @Test
  public void shouldCreateReadOnlyAdminRoleByDefault() {
    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .authorizationRecords()
                .withOwnerId("readonly-admin"))
        .extracting(Record::getValue)
        .describedAs(
            "Expect all readonly-admin role authorizations to be owned by the readonly-admin role")
        .allSatisfy(
            auth ->
                Assertions.assertThat(auth)
                    .hasOwnerId("readonly-admin")
                    .hasOwnerType(AuthorizationOwnerType.ROLE))
        .describedAs(
            "Expect all readonly-admin role authorizations to have the wildcard resource ID")
        .allSatisfy(auth -> Assertions.assertThat(auth).hasResourceId(WILDCARD.getResourceId()))
        .describedAs(
            "Expect the readonly-admin role authorizations to have specific read permissions only")
        .satisfiesExactlyInAnyOrder(
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.AUTHORIZATION)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.MAPPING_RULE)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.MESSAGE)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.COMPONENT)
                    .hasOnlyPermissionTypes(PermissionType.ACCESS),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.CLUSTER_VARIABLE)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.SYSTEM)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ,
                        PermissionType.READ_USAGE_METRIC,
                        PermissionType.READ_JOB_METRIC),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.TENANT)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.RESOURCE)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.READ_PROCESS_INSTANCE,
                        PermissionType.READ_USER_TASK),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DECISION_DEFINITION)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ_DECISION_DEFINITION,
                        PermissionType.READ_DECISION_INSTANCE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.GROUP)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.USER)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.USER_TASK)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.ROLE)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.BATCH)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DOCUMENT)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.AUDIT_LOG)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.GLOBAL_LISTENER)
                    .hasOnlyPermissionTypes(PermissionType.READ_TASK_LISTENER));
  }

  @Test
  public void shouldCreateConnectorsRoleByDefault() {
    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .authorizationRecords()
                .withOwnerId(DefaultRole.CONNECTORS.getId()))
        .extracting(Record::getValue)
        .describedAs("Expect all connectors role authorizations to be owned by the connectors role")
        .allSatisfy(
            auth ->
                Assertions.assertThat(auth)
                    .hasOwnerId(DefaultRole.CONNECTORS.getId())
                    .hasOwnerType(AuthorizationOwnerType.ROLE))
        .describedAs(
            "Expect the connectors role authorizations to have specific resource permissions")
        .satisfiesExactlyInAnyOrder(
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ_PROCESS_INSTANCE,
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.CREATE_PROCESS_INSTANCE,
                        PermissionType.UPDATE_PROCESS_INSTANCE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.MESSAGE)
                    .hasOnlyPermissionTypes(PermissionType.CREATE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.CLUSTER_VARIABLE)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.EXPRESSION)
                    .hasOnlyPermissionTypes(PermissionType.EVALUATE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DOCUMENT)
                    .hasOnlyPermissionTypes(
                        PermissionType.CREATE, PermissionType.READ, PermissionType.DELETE));
  }

  @Test
  public void shouldCreateAppsIntegrationRoleByDefault() {
    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .authorizationRecords()
                .withOwnerId(DefaultRole.APP_INTEGRATIONS.getId()))
        .extracting(Record::getValue)
        .describedAs(
            "Expect all apps_integration role authorizations to be owned by the apps_integration role")
        .allSatisfy(
            auth ->
                Assertions.assertThat(auth)
                    .hasOwnerId(DefaultRole.APP_INTEGRATIONS.getId())
                    .hasOwnerType(AuthorizationOwnerType.ROLE))
        .describedAs(
            "Expect the apps_integration role authorizations to have specific resource permissions")
        .satisfiesExactlyInAnyOrder(
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ_PROCESS_DEFINITION,
                        PermissionType.CREATE_PROCESS_INSTANCE,
                        PermissionType.READ_PROCESS_INSTANCE,
                        PermissionType.UPDATE_PROCESS_INSTANCE,
                        PermissionType.READ_USER_TASK,
                        PermissionType.UPDATE_USER_TASK),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.DOCUMENT)
                    .hasOnlyPermissionTypes(PermissionType.CREATE));
  }

  @Test
  public void shouldCreateRpaRoleByDefault() {
    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .authorizationRecords()
                .withOwnerId(DefaultRole.RPA.getId()))
        .extracting(Record::getValue)
        .describedAs("Expect all RPA role authorizations to be owned by the RPA role")
        .allSatisfy(
            auth ->
                Assertions.assertThat(auth)
                    .hasOwnerId(DefaultRole.RPA.getId())
                    .hasOwnerType(AuthorizationOwnerType.ROLE))
        .describedAs("Expect all RPA role authorizations to have the wildcard resource ID")
        .allSatisfy(auth -> Assertions.assertThat(auth).hasResourceId(WILDCARD.getResourceId()))
        .describedAs("Expect the RPA role authorizations to have specific resource permissions")
        .satisfiesExactlyInAnyOrder(
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.RESOURCE)
                    .hasOnlyPermissionTypes(PermissionType.READ),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .hasOnlyPermissionTypes(PermissionType.UPDATE_PROCESS_INSTANCE));
  }

  @Test
  public void shouldCreateTaskWorkerRoleByDefault() {
    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .authorizationRecords()
                .withOwnerId(DefaultRole.TASK_WORKER.getId()))
        .extracting(Record::getValue)
        .describedAs(
            "Expect all task-worker role authorizations to be owned by the task-worker role")
        .allSatisfy(
            auth ->
                Assertions.assertThat(auth)
                    .hasOwnerId(DefaultRole.TASK_WORKER.getId())
                    .hasOwnerType(AuthorizationOwnerType.ROLE))
        .describedAs("Expect all task-worker role authorizations to be for USER_TASK resource type")
        .allSatisfy(
            auth ->
                Assertions.assertThat(auth).hasResourceType(AuthorizationResourceType.USER_TASK))
        .describedAs(
            "Expect the task-worker role authorizations to have property-based permissions")
        .satisfiesExactlyInAnyOrder(
            auth ->
                Assertions.assertThat(auth)
                    .hasResourcePropertyName(UserTaskAuthorizationProperties.PROP_ASSIGNEE)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ, PermissionType.CLAIM, PermissionType.COMPLETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourcePropertyName(UserTaskAuthorizationProperties.PROP_CANDIDATE_USERS)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ, PermissionType.CLAIM, PermissionType.COMPLETE),
            auth ->
                Assertions.assertThat(auth)
                    .hasResourcePropertyName(UserTaskAuthorizationProperties.PROP_CANDIDATE_GROUPS)
                    .hasOnlyPermissionTypes(
                        PermissionType.READ, PermissionType.CLAIM, PermissionType.COMPLETE));
  }
}
