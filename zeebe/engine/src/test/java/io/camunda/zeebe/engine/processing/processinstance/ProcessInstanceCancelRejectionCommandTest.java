/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessInstanceCancelRejectionCommandTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TENANT = "custom-tenant";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldEnrichRejectionWhenTryingToCancelChildProcess() {
    // given - parent process with call activity
    final String parentProcessId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .userTask("childTask")
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("callActivity", ca -> ca.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withTenantId(TENANT).create();

    // Wait for the child process instance to be created
    final var childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withBpmnProcessId(childProcessId)
            .getFirst();

    final var childProcessInstanceKey = childProcessInstance.getValue().getProcessInstanceKey();

    // Wait for child task to be activated
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(childProcessInstanceKey)
        .withElementId("childTask")
        .await();

    // when - try to cancel the child process instance (should fail)
    final var rejectionRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(childProcessInstanceKey)
            .expectRejection()
            .cancel();

    // then - verify the rejection record is enriched
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceIntent.CANCEL)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be set to the parent (root) process instance key
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have rootProcessInstanceKey set to the parent process instance")
        .isEqualTo(parentProcessInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichRejectionWhenTryingToCancelDeeplyNestedChildProcess() {
    // given - parent -> child -> grandchild process hierarchy
    final String parentProcessId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String grandchildProcessId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(grandchildProcessId)
                .startEvent()
                .userTask("grandchildTask")
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .callActivity("callGrandchild", ca -> ca.zeebeProcessId(grandchildProcessId))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("callChild", ca -> ca.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var rootProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withTenantId(TENANT).create();

    // Wait for the grandchild process instance to be created
    final var grandchildProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(grandchildProcessId)
            .filter(r -> r.getValue().getRootProcessInstanceKey() == rootProcessInstanceKey)
            .getFirst();

    final var grandchildProcessInstanceKey =
        grandchildProcessInstance.getValue().getProcessInstanceKey();

    // Wait for grandchild task to be activated
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(grandchildProcessInstanceKey)
        .withElementId("grandchildTask")
        .await();

    // when - try to cancel the grandchild process instance (should fail)
    final var rejectionRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(grandchildProcessInstanceKey)
            .expectRejection()
            .cancel();

    // then - verify the rejection record is enriched with the ROOT process instance key
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceIntent.CANCEL)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be the root (parent) process instance key, not intermediate
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set to the ROOT process")
        .isEqualTo(rootProcessInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldNotEnrichRejectionWhenProcessInstanceNotFound() {
    // when - try to cancel a non-existing process instance
    final var rejectionRecord =
        ENGINE.processInstance().withInstanceKey(-1).onPartition(1).expectRejection().cancel();

    // then - verify the rejection is written but without enrichment
    // (since the process instance doesn't exist, we can't look up rootProcessInstanceKey)
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceIntent.CANCEL)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // rootProcessInstanceKey should be the default value (not set) since we couldn't look it up
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have default rootProcessInstanceKey when process instance not found")
        .isEqualTo(-1L);
  }
}
