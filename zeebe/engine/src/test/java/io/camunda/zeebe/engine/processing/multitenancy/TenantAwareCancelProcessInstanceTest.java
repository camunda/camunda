/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareCancelProcessInstanceTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withSecurityConfig(config -> config.getMultiTenancy().setEnabled(true));

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCancelInstanceForDefaultTenant() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .create();

    // when
    final var cancelled =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .forAuthorizedTenants(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .cancel();

    // then
    assertThat(cancelled)
        .describedAs("Expect that cancellation was successful")
        .hasIntent(ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  @Test
  public void shouldRejectCancelInstanceForUnauthorizedTenant() {
    // given
    final var tenantId = "another-tenant";
    final var username = "username";
    final var user = ENGINE.user().newUser(username).create().getValue();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .withTenantId("custom-tenant")
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withTenantId("custom-tenant").create();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .expectRejection()
            .cancel(user.getUsername());

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to cancel a process instance with key '%s', but no such process was found"
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldCancelInstanceForSpecificTenant() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .withTenantId("custom-tenant")
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process").withTenantId("custom-tenant").create();

    // when
    final var cancelled =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .forAuthorizedTenants("custom-tenant")
            .cancel();

    // then
    assertThat(cancelled)
        .describedAs("Expect that cancellation was successful")
        .hasIntent(ProcessInstanceIntent.ELEMENT_TERMINATED);
  }
}
