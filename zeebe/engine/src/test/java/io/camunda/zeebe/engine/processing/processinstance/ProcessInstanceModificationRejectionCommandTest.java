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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessInstanceModificationRejectionCommandTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @ClassRule
  public static final BrokerClassRuleHelper CLASS_RULE_HELPER = new BrokerClassRuleHelper();

  private static final String PROCESS_ID = "process";
  private static final String TENANT = "custom-tenant";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldEnrichRejectionCommandWhenElementNotFound() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .withTenantId(TENANT)
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when - try to activate a non-existing element
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("NON_EXISTING_ELEMENT")
        .expectRejection()
        .modify();

    // then - verify the rejection record is enriched with rootProcessInstanceKey
    final var rejectionRecord =
        RecordingExporter.processInstanceModificationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceModificationIntent.MODIFY)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    // The rootProcessInstanceKey should be set to processInstanceKey for a non-nested process
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichRejectionCommandWhenTerminateElementNotFound() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .withTenantId(TENANT)
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();
    final var userTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    // when - try to terminate a non-existing element instance
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(userTaskActivated.getKey())
        .terminateElement(99999L) // non-existing
        .expectRejection()
        .modify();

    // then - verify the rejection record is enriched
    final var rejectionRecord =
        RecordingExporter.processInstanceModificationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceModificationIntent.MODIFY)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

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
    final var childProcessId = "childProcess";
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
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .callActivity("callActivity", ca -> ca.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    // Get the child process instance
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

    // when - try to modify the child process with invalid element
    ENGINE
        .processInstance()
        .withInstanceKey(childProcessInstanceKey)
        .modification()
        .activateElement("NON_EXISTING_ELEMENT")
        .expectRejection()
        .modify();

    // then - verify the rejection record has the ROOT process instance key (parent), not the child
    final var rejectionRecord =
        RecordingExporter.processInstanceModificationRecords()
            .onlyCommandRejections()
            .withProcessInstanceKey(childProcessInstanceKey)
            .getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceModificationIntent.MODIFY)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

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
  public void shouldEnrichRejectionWhenAncestorScopeKeyNotFound() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .withTenantId(TENANT)
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when - try to activate with a non-existing ancestor scope key
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("A", 12345L) // non-existing ancestor
        .expectRejection()
        .modify();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceModificationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceModificationIntent.MODIFY)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldEnrichRejectionWhenVariableScopeNotFound() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .userTask("B")
                .done())
        .withTenantId(TENANT)
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when - try to set variables on a non-existing scope
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .withVariables("NON_EXISTING_SCOPE", java.util.Map.of("var", "value"))
        .expectRejection()
        .modify();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceModificationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceModificationIntent.MODIFY)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);

    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs("Rejection record should have rootProcessInstanceKey set")
        .isEqualTo(processInstanceKey);

    Assertions.assertThat(rejectionRecord.getValue().getTenantId())
        .describedAs("Rejection record should have tenantId set")
        .isEqualTo(TENANT);
  }

  @Test
  public void shouldNotSetRootProcessInstanceKeyWhenProcessInstanceNotFound() {
    // given - no process deployed, just use a random key
    final long unknownKey = 12345L;

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(unknownKey)
        .modification()
        .activateElement("A")
        .expectRejection()
        .modify();

    // then - the rejection should still be written but without enrichment
    // (since the process instance doesn't exist, we can't look up rootProcessInstanceKey)
    final var rejectionRecord =
        RecordingExporter.processInstanceModificationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceModificationIntent.MODIFY)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasKey(unknownKey);

    // rootProcessInstanceKey should be the default value (not set) since we couldn't look it up
    Assertions.assertThat(rejectionRecord.getValue().getRootProcessInstanceKey())
        .describedAs(
            "Rejection record should have default rootProcessInstanceKey when process instance not found")
        .isEqualTo(-1L);
  }
}
