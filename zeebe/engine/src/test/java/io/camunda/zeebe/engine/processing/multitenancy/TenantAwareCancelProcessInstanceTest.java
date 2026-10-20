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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantAwareCancelProcessInstanceTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withMultiTenancyChecksEnabled(true);

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
  public void shouldRejectCancelTerminatingInstanceForUnauthorizedTenant() {
    // given - a terminating instance whose cancel waits for a canceling task listener job
    final var processId = Strings.newRandomValidBpmnId();
    final var tenantId = Strings.newRandomValidBpmnId();
    final var otherTenantId = Strings.newRandomValidBpmnId();
    final var username = Strings.newRandomValidBpmnId();
    final var user = ENGINE.user().newUser(username).create().getValue();
    ENGINE.tenant().newTenant().withTenantId(otherTenantId).create();
    ENGINE
        .tenant()
        .addEntity(otherTenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask(
                    "task",
                    t -> t.zeebeUserTask().zeebeTaskListener(l -> l.canceling().type(processId)))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .await();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .forAuthorizedTenants(tenantId)
        .expectTerminating()
        .cancel();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(processId)
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .expectRejection()
            .cancel(user.getUsername());

    // then - the tenant check hides that the instance exists and is terminating
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
