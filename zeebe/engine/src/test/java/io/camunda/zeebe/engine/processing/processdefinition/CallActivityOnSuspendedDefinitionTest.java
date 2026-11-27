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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.UnaryOperator;
import org.junit.Rule;
import org.junit.Test;

/**
 * A call activity must not create a child instance of a suspended process definition. It raises an
 * incident instead, and resolving that incident after the resume creates the child.
 */
public final class CallActivityOnSuspendedDefinitionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsSuspendedProcessWithLatestBinding() {
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child = suspend(deploy(childModel(childId)));
    deploy(parentModel(parentId, c -> c.zeebeBindingType(ZeebeBindingType.latest), childId));

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertSuspendedIncident(parentInstanceKey, child);
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsSuspendedProcessWithDeploymentBinding() {
    // given - deployment binding resolves the called process within the same deployment
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child =
        suspend(
            engine
                .deployment()
                .withXmlResource("child.bpmn", childModel(childId))
                .withXmlResource(
                    "parent.bpmn",
                    parentModel(
                        parentId, c -> c.zeebeBindingType(ZeebeBindingType.deployment), childId))
                .deploy()
                .getValue()
                .getProcessesMetadata()
                .stream()
                .filter(p -> p.getBpmnProcessId().equals(childId))
                .findFirst()
                .orElseThrow());

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertSuspendedIncident(parentInstanceKey, child);
  }

  @Test
  public void shouldRaiseIncidentWhenCallActivityCallsSuspendedProcessWithVersionTagBinding() {
    // given
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child =
        suspend(
            deploy(
                Bpmn.createExecutableProcess(childId)
                    .versionTag("v1")
                    .startEvent()
                    .endEvent()
                    .done()));
    deploy(
        parentModel(
            parentId,
            c -> c.zeebeBindingType(ZeebeBindingType.versionTag).zeebeVersionTag("v1"),
            childId));

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertSuspendedIncident(parentInstanceKey, child);
  }

  @Test
  public void shouldRaiseIncidentWhenLatestVersionIsSuspendedEvenIfOlderVersionIsActive() {
    // given - the latest binding does not fall back to an older active version
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    deploy(childModel(childId));
    final var suspendedVersion =
        suspend(
            deploy(
                Bpmn.createExecutableProcess(childId)
                    .startEvent()
                    .manualTask("second-version")
                    .endEvent()
                    .done()));
    assertThat(suspendedVersion.getVersion()).isEqualTo(2);
    deploy(parentModel(parentId, c -> c.zeebeBindingType(ZeebeBindingType.latest), childId));

    // when
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();

    // then
    assertSuspendedIncident(parentInstanceKey, suspendedVersion);
  }

  @Test
  public void shouldCreateChildAfterResumeAndIncidentResolution() {
    // given - a call activity with an incident because the child definition is suspended
    final var childId = helper.getBpmnProcessId() + "-child";
    final var parentId = helper.getBpmnProcessId() + "-parent";
    final var child = suspend(deploy(childModel(childId)));
    deploy(parentModel(parentId, c -> c.zeebeBindingType(ZeebeBindingType.latest), childId));
    final long parentInstanceKey = engine.processInstance().ofBpmnProcessId(parentId).create();
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(parentInstanceKey)
            .getFirst();

    // when
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(child.getProcessDefinitionKey())
        .resume();
    engine.incident().ofInstance(parentInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(childId)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue()
                .getProcessDefinitionKey())
        .isEqualTo(child.getProcessDefinitionKey());
  }

  private static BpmnModelInstance childModel(final String childId) {
    return Bpmn.createExecutableProcess(childId).startEvent().endEvent().done();
  }

  private static BpmnModelInstance parentModel(
      final String parentId,
      final UnaryOperator<CallActivityBuilder> binding,
      final String childId) {
    return Bpmn.createExecutableProcess(parentId)
        .startEvent()
        .callActivity("call", c -> binding.apply(c.zeebeProcessId(childId)))
        .endEvent()
        .done();
  }

  private ProcessMetadataValue deploy(final BpmnModelInstance model) {
    return engine
        .deployment()
        .withXmlResource(model)
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private ProcessMetadataValue suspend(final ProcessMetadataValue process) {
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(process.getProcessDefinitionKey())
        .suspend();
    return process;
  }

  private Record<IncidentRecordValue> assertSuspendedIncident(
      final long parentInstanceKey, final ProcessMetadataValue child) {
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(parentInstanceKey)
            .getFirst();
    assertThat(incident.getValue().getErrorType()).isEqualTo(ErrorType.CALLED_ELEMENT_ERROR);
    assertThat(incident.getValue().getElementId()).isEqualTo("call");
    assertThat(incident.getValue().getErrorMessage())
        .isEqualTo(
            "Expected to call process with BPMN process id '%s' and version %d (key %d), but it is suspended."
                .formatted(
                    child.getBpmnProcessId(), child.getVersion(), child.getProcessDefinitionKey()));
    return incident;
  }
}
