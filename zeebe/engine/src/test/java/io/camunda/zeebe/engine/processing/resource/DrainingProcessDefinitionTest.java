/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that no new process instances can be created for a process definition that is in the
 * {@link io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState#DRAINING}
 * state.
 */
public class DrainingProcessDefinitionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCreateInstanceByProcessIdWhenDraining() {
    // given
    final var processId = helper.getBpmnProcessId();
    drain(deploy(processId));

    // when
    engine.processInstance().ofBpmnProcessId(processId).expectRejection().create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    io.camunda.zeebe.protocol.record.Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to create instance of process '%s', but it is being deleted"
                .formatted(processId));
  }

  @Test
  public void shouldRejectCreateInstanceByVersionWhenDraining() {
    // given
    final var processId = helper.getBpmnProcessId();
    drain(deploy(processId));

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVersion(1).expectRejection().create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();
    io.camunda.zeebe.protocol.record.Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to create instance of process '%s', but it is being deleted"
                .formatted(processId));
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsDrainingProcessWithLatestBinding() {
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child = deploy(childId);
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentId)
                .startEvent()
                .callActivity(
                    "call",
                    c -> c.zeebeProcessId(childId).zeebeBindingType(ZeebeBindingType.latest))
                .endEvent()
                .done())
        .deploy();
    drain(child);

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertCalledElementIncident(parentInstanceKey, childId);
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsDrainingProcessWithDeploymentBinding() {
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    // deployment binding resolves the called process within the same deployment
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                "child.bpmn", Bpmn.createExecutableProcess(childId).startEvent().endEvent().done())
            .withXmlResource(
                "parent.bpmn",
                Bpmn.createExecutableProcess(parentId)
                    .startEvent()
                    .callActivity(
                        "call",
                        c ->
                            c.zeebeProcessId(childId).zeebeBindingType(ZeebeBindingType.deployment))
                    .endEvent()
                    .done())
            .deploy();
    final var child =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(p -> p.getBpmnProcessId().equals(childId))
            .findFirst()
            .orElseThrow();
    drain(child);

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertCalledElementIncident(parentInstanceKey, childId);
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsDrainingProcessWithVersionTagBinding() {
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(childId)
                    .versionTag("v1")
                    .startEvent()
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentId)
                .startEvent()
                .callActivity(
                    "call",
                    c ->
                        c.zeebeProcessId(childId)
                            .zeebeBindingType(ZeebeBindingType.versionTag)
                            .zeebeVersionTag("v1"))
                .endEvent()
                .done())
        .deploy();
    drain(child);

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertCalledElementIncident(parentInstanceKey, childId);
  }

  private void assertCalledElementIncident(final long parentInstanceKey, final String childId) {
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(parentInstanceKey)
            .getFirst();
    org.assertj.core.api.Assertions.assertThat(incident.getValue().getErrorType())
        .isEqualTo(ErrorType.CALLED_ELEMENT_ERROR);
    org.assertj.core.api.Assertions.assertThat(incident.getValue().getErrorMessage())
        .isEqualTo(
            "Expected to call process with BPMN process id '%s', but it is being deleted."
                .formatted(childId));
  }

  @Test
  public void shouldRejectMigrationToDrainingTargetProcess() {
    // given
    final var sourceId = helper.getBpmnProcessId() + "-source";
    final var targetId = helper.getBpmnProcessId() + "-target";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(sourceId).startEvent().userTask("A").endEvent().done())
        .deploy();
    final var target = deploy(targetId, "B");

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(sourceId).create();
    RecordingExporter.processInstanceRecords(
            io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(io.camunda.zeebe.protocol.record.value.BpmnElementType.USER_TASK)
        .await();
    drain(target);
    final long targetProcessDefinitionKey = target.getProcessDefinitionKey();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .expectRejection()
            .migrate();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to migrate process instance to process definition with key '%d' but it is being deleted"
                .formatted(targetProcessDefinitionKey));
  }

  private ProcessMetadataValue deploy(final String processId) {
    return deploy(processId, null);
  }

  private ProcessMetadataValue deploy(final String processId, final String userTaskId) {
    final var builder = Bpmn.createExecutableProcess(processId).startEvent();
    if (userTaskId != null) {
      builder.userTask(userTaskId);
    }
    return engine
        .deployment()
        .withXmlResource(builder.endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0);
  }

  /**
   * Puts the given process definition into the {@code DRAINING} state. Since no processor writes
   * the {@code DRAINING} event yet, the event is injected onto the log while the engine is stopped
   * so it is applied to state on the next start (replay), rather than being ignored during live
   * processing. TODO(#56978): drive draining via a real {@code RESOURCE_DELETION.DELETE} once that
   * change lands, and remove this injection helper.
   */
  private void drain(final ProcessMetadataValue metadata) {
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .key(metadata.getProcessDefinitionKey())
            .process(
                ProcessIntent.DRAINING,
                new ProcessRecord()
                    .setKey(metadata.getProcessDefinitionKey())
                    .setBpmnProcessId(metadata.getBpmnProcessId())
                    .setVersion(metadata.getVersion())
                    .setResourceName(metadata.getResourceName())
                    .setTenantId(metadata.getTenantId())));
    engine.start();

    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
  }
}
