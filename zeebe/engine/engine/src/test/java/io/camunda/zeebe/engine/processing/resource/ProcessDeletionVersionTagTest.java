/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessDeletionVersionTagTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCallProcessByVersionTagAfterDeletingOlderVersionWithSameTag() {
    // given
    final var childProcessId = helper.getBpmnProcessId() + "-child";
    final var parentProcessId = deployParentCallingByVersionTag(childProcessId, "shared");
    final var v1 = deploy(childProcessId, "shared", "v1");
    final var v2 = deploy(childProcessId, "shared", "v2");

    // when
    deleteProcess(v1);

    // then
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    assertThat(childProcess(processInstanceKey).getProcessDefinitionKey())
        .isEqualTo(v2.getProcessDefinitionKey());
  }

  @Test
  public void shouldCallProcessByVersionTagAfterDeletingLatestVersionWithSameTag() {
    // given
    final var childProcessId = helper.getBpmnProcessId() + "-child";
    final var parentProcessId = deployParentCallingByVersionTag(childProcessId, "shared");
    final var v1 = deploy(childProcessId, "shared", "v1");
    final var v2 = deploy(childProcessId, "shared", "v2");

    // when
    deleteProcess(v2);

    // then
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    assertThat(childProcess(processInstanceKey).getProcessDefinitionKey())
        .isEqualTo(v1.getProcessDefinitionKey());
  }

  @Test
  public void shouldCallProcessByVersionTagAfterDrainedLatestVersionWithSameTagIsDeleted() {
    // given
    final var childProcessId = helper.getBpmnProcessId() + "-child";
    final var parentProcessId = deployParentCallingByVersionTag(childProcessId, "shared");
    final var v1 = deploy(childProcessId, "shared", "v1");
    final var v2 =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(childProcessId)
                    .versionTag("shared")
                    .startEvent("v2")
                    .serviceTask("task", t -> t.zeebeJobType(helper.getJobType()))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();
    final long runningInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(childProcessId).create();
    deleteProcess(v2);
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(v2.getProcessDefinitionKey())
        .await();

    // when
    ENGINE.job().ofInstance(runningInstanceKey).withType(helper.getJobType()).complete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETED)
        .withProcessDefinitionKey(v2.getProcessDefinitionKey())
        .await();

    // then
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    assertThat(childProcess(processInstanceKey).getProcessDefinitionKey())
        .isEqualTo(v1.getProcessDefinitionKey());
  }

  @Test
  public void shouldCreateIncidentAfterDeletingAllVersionsWithVersionTag() {
    // given
    final var childProcessId = helper.getBpmnProcessId() + "-child";
    final var parentProcessId = deployParentCallingByVersionTag(childProcessId, "shared");
    final var v1 = deploy(childProcessId, "shared", "v1");
    final var v2 = deploy(childProcessId, "shared", "v2");
    deploy(childProcessId, "other", "v3");

    // when
    deleteProcess(v2);
    deleteProcess(v1);

    // then
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .extracting(IncidentRecordValue::getErrorType, IncidentRecordValue::getErrorMessage)
        .containsExactly(
            ErrorType.CALLED_ELEMENT_ERROR,
            """
            Expected to call process with BPMN process id '%s' and version tag '%s', but no such process found. \
            To resolve this incident, deploy a process with the given process id and version tag.\
            """
                .formatted(childProcessId, "shared"));
  }

  private ProcessMetadataValue deploy(
      final String processId, final String versionTag, final String startEventId) {
    return ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .versionTag(versionTag)
                .startEvent(startEventId)
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private String deployParentCallingByVersionTag(
      final String childProcessId, final String versionTag) {
    final var parentProcessId = helper.getBpmnProcessId() + "-parent";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity(
                    "call",
                    c ->
                        c.zeebeProcessId(childProcessId)
                            .zeebeBindingType(ZeebeBindingType.versionTag)
                            .zeebeVersionTag(versionTag))
                .endEvent()
                .done())
        .deploy();
    return parentProcessId;
  }

  private void deleteProcess(final ProcessMetadataValue process) {
    ENGINE.resourceDeletion().withResourceKey(process.getProcessDefinitionKey()).delete();
  }

  private ProcessInstanceRecordValue childProcess(final long parentProcessInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withParentProcessInstanceKey(parentProcessInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .getFirst()
        .getValue();
  }
}
