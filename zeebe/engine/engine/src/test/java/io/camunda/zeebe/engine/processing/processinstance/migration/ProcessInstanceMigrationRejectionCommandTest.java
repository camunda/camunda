/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessInstanceMigrationRejectionCommandTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TENANT = "custom-tenant";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldEnrichRejectionCommandWhenActiveElementNotMapped() {
    // given - deploy source and target processes
    final String sourceProcessId = Strings.newRandomValidBpmnId();
    final String targetProcessId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(sourceProcessId)
                .startEvent()
                .serviceTask("A", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var targetDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .done())
            .withTenantId(TENANT)
            .deploy();

    final long targetProcessDefinitionKey =
        targetDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).withTenantId(TENANT).create();

    // Wait for the service task to be activated
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when - try to migrate without providing mapping for active element
    final var rejectionRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .expectRejection()
            .migrate();

    // then - verify the rejection record is enriched
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be set (for a non-nested process, it equals the process
    // instance key)
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichRejectionCommandForChildProcess() {
    // given - parent process with call activity
    final String parentProcessId = Strings.newRandomValidBpmnId();
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String targetChildProcessId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType("task"))
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

    final var targetDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(targetChildProcessId)
                    .startEvent()
                    .serviceTask("differentTask", t -> t.zeebeJobType("task"))
                    .endEvent()
                    .done())
            .withTenantId(TENANT)
            .deploy();

    final long targetProcessDefinitionKey =
        targetDeployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withTenantId(TENANT).create();

    // Wait for the child process instance to be created
    final var childProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withBpmnProcessId(childProcessId)
            .getFirst();

    final var childProcessInstanceKey = childProcessInstance.getValue().getProcessInstanceKey();

    // Wait for the child task to be activated
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(childProcessInstanceKey)
        .withElementId("childTask")
        .await();

    // when - try to migrate the child process with invalid mapping (no mapping for active element)
    final var rejectionRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(childProcessInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .expectRejection()
            .migrate();

    // then - verify the rejection record has the ROOT process instance key (parent), not the child
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.INVALID_STATE);

    // The rootProcessInstanceKey should be the parent (root) process instance key
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have rootProcessInstanceKey set to the parent process instance")
        .isEqualTo(parentProcessInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichRejectionWhenTargetProcessDefinitionNotFound() {
    // given
    final String sourceProcessId = Strings.newRandomValidBpmnId();
    final String tenantId = Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(sourceProcessId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(sourceProcessId).withTenantId(tenantId).create();

    // Wait for the service task to be activated
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .await();

    final long unknownTargetKey = 99999L;

    // when - try to migrate to unknown target process definition
    final var rejectionRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(unknownTargetKey)
            .expectRejection()
            .migrate();

    // then - verify the rejection record is enriched
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // The rootProcessInstanceKey should still be set since we found the source process instance
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(tenantId);
  }

  @Test
  public void shouldNotEnrichRejectionWhenProcessInstanceNotFound() {
    // given - no source process instance exists
    final long unknownProcessInstanceKey = 12345L;

    // when - try to migrate a non-existing process instance
    final var rejectionRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(unknownProcessInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(1L)
            .expectRejection()
            .migrate();

    // then - verify the rejection is written but without enrichment
    // (since the process instance doesn't exist, we can't look up rootProcessInstanceKey)
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE)
        .hasRejectionType(RejectionType.NOT_FOUND);

    // rootProcessInstanceKey should be the default value (not set) since we couldn't look it up
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have default rootProcessInstanceKey when process instance not found")
        .isEqualTo(-1L);
  }
}
