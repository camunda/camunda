/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins that rejections written before any processor runs (suspension gate, banned instance) and
 * processor rejections carry the storage ordinal of the instance, restored at the engine entry.
 */
public final class StorageOrdinalRejectionTest {

  private static final int FIXED_ORDINAL = 1234;
  private static final String JOB_TYPE = "ordinal-task";
  private static final String USERNAME = "ordinal-user";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinals(List.of(FIXED_ORDINAL)));

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAssignConfiguredOrdinalToSuspensionGateRejection() {
    // given
    final long processInstanceKey = deployAndStart("ordinal-rejection-suspended");
    final long jobKey = jobKeyOf(processInstanceKey);
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    engine.job().withKey(jobKey).expectRejection().complete();

    // then
    final var rejection =
        RecordingExporter.jobRecords(JobIntent.COMPLETE)
            .onlyCommandRejections()
            .withRecordKey(jobKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("suspended");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToBannedInstanceRejection() {
    // given
    final long processInstanceKey = deployAndStart("ordinal-rejection-banned");
    jobKeyOf(processInstanceKey);
    engine.banInstanceInNewTransaction(1, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("task")
        .expectRejection()
        .modify();

    // then
    final var rejection =
        RecordingExporter.processInstanceModificationRecords(
                ProcessInstanceModificationIntent.MODIFY)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("banned");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessorRejection() {
    // given - the complete processor rejects a failed job
    final long processInstanceKey = deployAndStart("ordinal-rejection-processor");
    final long jobKey = jobKeyOf(processInstanceKey);
    engine.jobs().withType(JOB_TYPE).activate();
    engine.job().withKey(jobKey).withRetries(0).fail();

    // when
    engine.job().withKey(jobKey).expectRejection().complete();

    // then
    final var rejection =
        RecordingExporter.jobRecords(JobIntent.COMPLETE)
            .onlyCommandRejections()
            .withRecordKey(jobKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldKeepAssignedOrdinalOnRejectionAfterArchiverlessModeIsTurnedOff() {
    // given - an instance created and suspended while the mode assigned ordinals
    final long processInstanceKey = deployAndStart("ordinal-rejection-mode-off");
    final long jobKey = jobKeyOf(processInstanceKey);
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    engine.stop();
    engine.withEngineConfig(c -> c.setArchiverlessEnabled(false));
    engine.start();

    // when
    engine.job().withKey(jobKey).expectRejection().complete();

    // then
    final var rejection =
        RecordingExporter.jobRecords(JobIntent.COMPLETE)
            .onlyCommandRejections()
            .withRecordKey(jobKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToRejectionOfCommandKeyedByTheInstanceOnly() {
    // given - the gateway writes SUSPEND with the instance key as record key and an empty value
    final long processInstanceKey = deployAndStart("ordinal-rejection-key-only");
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SUSPENDED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .key(processInstanceKey)
            .processInstance(ProcessInstanceIntent.SUSPEND, new ProcessInstanceRecord()));

    // then
    final var rejection =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SUSPEND)
            .onlyCommandRejections()
            .withRecordKey(processInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("already suspended");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToBannedInstanceRejectionOfCorrelateWithoutOrdinal() {
    // given - a subscription created before the ordinal existed has a CORRELATE without ordinal
    final String processId = "ordinal-rejection-correlate-process";
    final String messageName = "ordinal-rejection-correlate-message";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e -> e.message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVariable("key", "k1").create();
    final var created =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    // and - the instance is banned
    engine.banInstanceInNewTransaction(1, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();

    // when
    final var correlate =
        new ProcessMessageSubscriptionRecord()
            .setProcessInstanceKey(created.getProcessInstanceKey())
            .setElementInstanceKey(created.getElementInstanceKey())
            .setMessageName(BufferUtil.wrapString(created.getMessageName()))
            .setCorrelationKey(BufferUtil.wrapString(created.getCorrelationKey()))
            .setSubscriptionPartitionId(1)
            .setTenantId(created.getTenantId())
            .setMessageKey(1L);
    engine.writeRecords(
        RecordToWrite.command()
            .processMessageSubscription(ProcessMessageSubscriptionIntent.CORRELATE, correlate));

    // then
    final var rejection =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATE)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("banned");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSuspensionGateRejectionOfUserTaskCommand() {
    // given
    final long processInstanceKey = deployUserTaskAndStart("ordinal-rejection-user-task");
    final long userTaskKey = userTaskOf(processInstanceKey).getKey();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    engine.userTask().withKey(userTaskKey).expectRejection().complete();

    // then
    final var rejection =
        RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETE)
            .onlyCommandRejections()
            .withRecordKey(userTaskKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("suspended");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSuspensionGateRejectionOfIncidentResolve() {
    // given - an incident raised by a job that failed without retries
    final long processInstanceKey = deployAndStart("ordinal-rejection-incident");
    final long jobKey = jobKeyOf(processInstanceKey);
    engine.jobs().withType(JOB_TYPE).activate();
    engine.job().withKey(jobKey).withRetries(0).fail();
    final long incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - a user name adds request metadata, so the gate sees a client command and rejects
    engine
        .incident()
        .ofInstance(processInstanceKey)
        .withKey(incidentKey)
        .expectRejection()
        .resolve(USERNAME);

    // then
    final var rejection =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVE)
            .onlyCommandRejections()
            .withRecordKey(incidentKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("suspended");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSuspensionGateRejectionOfVariableUpdateOnUserTask() {
    // given - the scope of the update is the element instance of a user task
    final long processInstanceKey = deployUserTaskAndStart("ordinal-rejection-variable-scope");
    final long scopeKey = userTaskOf(processInstanceKey).getValue().getElementInstanceKey();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    engine.variables().ofScope(scopeKey).withDocument(Map.of("x", 1)).expectRejection().update();

    // then
    final var rejection =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATE)
            .onlyCommandRejections()
            .withScopeKey(scopeKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("suspended");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSuspensionGateRejectionOfAdHocActivation() {
    // given
    final String processId = "ordinal-rejection-ad-hoc";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst()
            .getKey();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - a user name adds request metadata, so the gate sees a client command and rejects
    engine
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIds("A")
        .expectRejection()
        .activate(USERNAME);

    // then
    final var rejection =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.ACTIVATE)
            .onlyCommandRejections()
            .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("suspended");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToRejectionOfCalledChildInstanceCommand() {
    // given - the command names the child instance, so the resolver reads the child, not the root
    final String childId = "ordinal-rejection-child";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ordinal-rejection-parent")
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childId))
                .endEvent()
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId("ordinal-rejection-parent").create();
    final long childKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(childId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();
    final long jobKey = jobKeyOf(childKey);
    engine.jobs().withType(JOB_TYPE).activate();
    engine.job().withKey(jobKey).withRetries(0).fail();

    // when
    engine.job().withKey(jobKey).expectRejection().complete();

    // then
    final var rejection =
        RecordingExporter.jobRecords(JobIntent.COMPLETE)
            .onlyCommandRejections()
            .withRecordKey(jobKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("is in state 'FAILED'");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSuspensionGateRejectionOfTenantInstance() {
    // given
    final String processId = "ordinal-rejection-tenant";
    final String tenantId = "tenant-ordinal";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();
    final long jobKey = jobKeyOf(processInstanceKey);
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .forAuthorizedTenants(tenantId)
        .suspend();

    // when
    engine.job().withKey(jobKey).withAuthorizedTenantIds(tenantId).expectRejection().complete();

    // then
    final var rejection =
        RecordingExporter.jobRecords(JobIntent.COMPLETE)
            .onlyCommandRejections()
            .withRecordKey(jobKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("suspended");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private long deployAndStart(final String processId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId(processId).create();
  }

  private long deployUserTaskAndStart(final String processId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task", t -> t.zeebeUserTask())
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId(processId).create();
  }

  private static Record<UserTaskRecordValue> userTaskOf(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  private static long jobKeyOf(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }
}
