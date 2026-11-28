/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that no new process instances are created on a suspended process definition version,
 * while creation by BPMN process ID only falls back to the latest version that isn't suspended.
 */
public class CreateProcessInstanceOnSuspendedDefinitionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCreateInstanceByKeyWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId, "v1");
    suspend(metadata);

    // when
    engine
        .processInstance()
        .ofBpmnProcessId("")
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .expectRejection()
        .create();

    // then
    assertSuspendedRejection(ProcessInstanceCreationIntent.CREATE, metadata);
  }

  @Test
  public void shouldRejectCreateInstanceByVersionWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId, "v1");
    suspend(metadata);

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVersion(1).expectRejection().create();

    // then
    assertSuspendedRejection(ProcessInstanceCreationIntent.CREATE, metadata);
  }

  @Test
  public void shouldRejectCreateInstanceByVersionTagWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata =
        deploy(Bpmn.createExecutableProcess(processId).versionTag("v1.0").startEvent().done());
    suspend(metadata);

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersionTag("v1.0")
        .expectRejection()
        .create();

    // then
    assertSuspendedRejection(ProcessInstanceCreationIntent.CREATE, metadata);
  }

  @Test
  public void shouldRejectCreateInstanceWithResultWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId, "v1");
    suspend(metadata);

    // when
    engine
        .processInstance()
        .ofBpmnProcessId("")
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .withResult()
        .asyncCreate();

    // then
    assertSuspendedRejection(ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT, metadata);
  }

  @Test
  public void shouldRejectCreateInstanceWithStartInstructionsWhenSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId, "v1");
    suspend(metadata);

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersion(1)
        .withStartInstruction("v1")
        .expectRejection()
        .create();

    // then
    assertSuspendedRejection(ProcessInstanceCreationIntent.CREATE, metadata);
  }

  @Test
  public void shouldCreateInstanceByProcessIdOnLatestActiveVersionWhenLatestIsSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processId, "v1");
    final var v2 = deploy(processId, "v2");
    suspend(v2);

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertActivatedOn(processInstanceKey, v1);
  }

  @Test
  public void shouldRejectCreateInstanceByProcessIdWhenAllVersionsAreSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    suspend(deploy(processId, "v1"));
    suspend(deploy(processId, "v2"));

    // when
    engine.processInstance().ofBpmnProcessId(processId).expectRejection().create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst())
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to find process definition with process ID '%s', but no active definition was found"
                .formatted(processId));
  }

  @Test
  public void shouldCreateInstanceOnActiveVersionWhenOtherVersionIsSuspended() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processId, "v1");
    final var v2 = deploy(processId, "v2");
    suspend(v1);

    // when
    final long byVersion =
        engine.processInstance().ofBpmnProcessId(processId).withVersion(2).create();
    final long byKey =
        engine
            .processInstance()
            .ofBpmnProcessId("")
            .withProcessDefinitionKey(v2.getProcessDefinitionKey())
            .create();

    // then
    assertActivatedOn(byVersion, v2);
    assertActivatedOn(byKey, v2);
  }

  @Test
  public void shouldCreateInstanceAfterResume() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId, "v1");
    suspend(metadata);
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .resume();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVersion(1).create();

    // then
    assertActivatedOn(processInstanceKey, metadata);
  }

  @Test
  public void shouldRejectProcessActivationWhenSuspendedAfterCreation() {
    // given - a suspended version and the ACTIVATE_ELEMENT command a creation accepted before the
    // suspend would have written for it
    final var processId = helper.getBpmnProcessId();
    final var metadata = deploy(processId, "v1");
    suspend(metadata);

    final long processInstanceKey = 123L;
    final var record =
        new ProcessInstanceRecord()
            .setBpmnProcessId(processId)
            .setProcessDefinitionKey(metadata.getProcessDefinitionKey())
            .setVersion(metadata.getVersion())
            .setProcessInstanceKey(processInstanceKey)
            .setElementId(processId)
            .setBpmnElementType(BpmnElementType.PROCESS)
            .setTenantId(metadata.getTenantId());

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .key(processInstanceKey)
            .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, record));

    // then
    final var rejection =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .onlyCommandRejections()
            .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
            .getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(suspendedReason(metadata));
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= rejection.getPosition())
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldActivateProcessAfterIncidentResolutionWhenSuspended() {
    // given - an instance whose start execution listener raised an incident while the process was
    // activating, and whose version was suspended afterwards
    final var processId = helper.getBpmnProcessId();
    final var metadata =
        deploy(
            Bpmn.createExecutableProcess(processId)
                .zeebeExecutionListener(el -> el.start().typeExpression("listenerType"))
                .startEvent()
                .endEvent()
                .done());
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    suspend(metadata);

    // when
    engine
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("listenerType", "listener"))
        .update();
    engine.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();
    engine.job().ofInstance(processInstanceKey).withType("listener").complete();

    // then - the existing instance continues; suspension only blocks new instances
    assertActivatedOn(processInstanceKey, metadata);
  }

  private ProcessMetadataValue deploy(final String processId, final String endEventId) {
    return deploy(Bpmn.createExecutableProcess(processId).startEvent().endEvent(endEventId).done());
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

  private void suspend(final ProcessMetadataValue metadata) {
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .suspend();
  }

  private void assertSuspendedRejection(
      final ProcessInstanceCreationIntent intent, final ProcessMetadataValue metadata) {
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(intent)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(suspendedReason(metadata));
  }

  private static String suspendedReason(final ProcessMetadataValue metadata) {
    return ProcessInstanceCreationHelper.ERROR_MESSAGE_PROCESS_IS_SUSPENDED.formatted(
        metadata.getBpmnProcessId(), metadata.getVersion(), metadata.getProcessDefinitionKey());
  }

  private void assertActivatedOn(
      final long processInstanceKey, final ProcessMetadataValue metadata) {
    final var activated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(activated.getValue().getProcessDefinitionKey())
        .isEqualTo(metadata.getProcessDefinitionKey());
  }
}
