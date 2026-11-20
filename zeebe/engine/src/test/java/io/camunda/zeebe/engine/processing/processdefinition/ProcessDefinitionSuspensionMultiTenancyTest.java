/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** A caller only reaches process definitions of the tenants they are assigned to. */
public class ProcessDefinitionSuspensionMultiTenancyTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withAuthorizationsEnabled(true)
          .withMultiTenancyChecksEnabled(true);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String tenantId;
  private String username;
  private ProcessMetadataValue process;

  @Before
  public void setUp() {
    tenantId = Strings.newRandomValidTenantId();
    username = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.SUSPEND_PROCESS_DEFINITION)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withResourceMatcher(AuthorizationResourceMatcher.ANY)
        .withResourceId("*")
        .create();
    process =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(Strings.newRandomValidBpmnId())
                    .startEvent()
                    .endEvent()
                    .done())
            .withTenantId(tenantId)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();
  }

  @Test
  public void shouldSuspendInAssignedTenant() {
    // given
    assignUserToTenant();

    // when
    final var suspended =
        ENGINE
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .withTenantId(tenantId)
            .withAuthorizedTenantIds(tenantId)
            .suspend(username);

    // then
    assertThat(suspended.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPENDED);
  }

  @Test
  public void shouldSuspendByKeyInAssignedTenantWithoutTenantId() {
    // given
    assignUserToTenant();

    // when - like the REST API, a request by key carries no tenant
    final var suspended =
        ENGINE
            .processDefinitionSuspension()
            .withProcessDefinitionKey(process.getProcessDefinitionKey())
            .withAuthorizedTenantIds(tenantId)
            .suspend(username);

    // then
    assertThat(suspended.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPENDED);
    assertThat(suspended.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey)
        .containsExactly(process.getProcessDefinitionKey());
  }

  @Test
  public void shouldRejectBpmnProcessIdOfUnassignedTenantAsNotFound() {
    // when
    final var rejection =
        ENGINE
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .withTenantId(tenantId)
            .expectRejection()
            .suspend(username);

    // then - same rejection as for an unknown BPMN process ID
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to suspend process definition with BPMN process ID '%s', but no such process definition was found"
                .formatted(process.getBpmnProcessId()));
  }

  @Test
  public void shouldRejectKeyOfUnassignedTenantAsNotFound() {
    // when
    final var rejection =
        ENGINE
            .processDefinitionSuspension()
            .withProcessDefinitionKey(process.getProcessDefinitionKey())
            .expectRejection()
            .resume(username);

    // then - same rejection as for an unknown key
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to resume process definition with key '%d', but no such process definition was found"
                .formatted(process.getProcessDefinitionKey()));
  }

  private void assignUserToTenant() {
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
  }
}
