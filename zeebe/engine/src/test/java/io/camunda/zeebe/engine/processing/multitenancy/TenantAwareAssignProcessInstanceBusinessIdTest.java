/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Verifies that late Business ID assignment (ADR 0006) respects multi-tenancy: the ASSIGN command
 * is rejected when the caller is not authorized for the tenant that owns the process instance, and
 * succeeds when it is.
 */
public class TenantAwareAssignProcessInstanceBusinessIdTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withMultiTenancyChecksEnabled(true)
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));

  private static final String PROCESS_ID = "process";
  private static final String CUSTOM_TENANT = "custom-tenant";
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectAssignBusinessIdForUnauthorizedTenant() {
    // given
    final var username = "username";
    final var user = ENGINE.user().newUser(username).create().getValue();
    ENGINE.tenant().newTenant().withTenantId("another-tenant").create();
    ENGINE
        .tenant()
        .addEntity("another-tenant")
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .withTenantId(CUSTOM_TENANT)
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(CUSTOM_TENANT).create();

    // when: the user is not authorized for the tenant that owns the process instance
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .businessIdAssignment()
            .withBusinessId("biz-1")
            .expectRejection()
            .assign(user.getUsername());

    // then
    assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldAssignBusinessIdForAuthorizedTenant() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("task", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent()
                .done())
        .withTenantId(CUSTOM_TENANT)
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(CUSTOM_TENANT).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .forAuthorizedTenants(CUSTOM_TENANT)
        .businessIdAssignment()
        .withBusinessId("biz-1")
        .assign();

    // then
    final var assigned =
        RecordingExporter.processInstanceBusinessIdRecords(ProcessInstanceBusinessIdIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();
    assertThat(assigned.getBusinessId()).isEqualTo("biz-1");
    assertThat(assigned.getTenantId()).isEqualTo(CUSTOM_TENANT);
  }
}
