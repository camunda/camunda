/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessDefinitionRestoreRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionRestoreIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessDefinitionRestoreTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRestoreStrayPendingDeletionDefinitionOnRecovery() {
    // given - a definition stuck in PENDING_DELETION by the pre-8.9.18 write bug
    final var processId = Strings.newRandomValidBpmnId();
    final var v1 = deploy(simpleProcess(processId));
    injectPendingDeletion(v1);

    // when - enable the restore and restart
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();

    // then - the cleanup runs its full lifecycle and flips the stray row back to ACTIVE
    awaitRestoreFinishedAndCommitted();

    final var restored =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.RESTORED)
            .withRecordKey(v1.getProcessDefinitionKey())
            .getFirst();
    assertThat(restored.getValue().getTenantId()).isEqualTo(v1.getTenantId());

    assertThat(
            engine
                .getProcessingState()
                .getProcessState()
                .getProcessByKeyAndTenant(v1.getProcessDefinitionKey(), v1.getTenantId())
                .getState())
        .isEqualTo(PersistedProcessState.ACTIVE);
  }

  @Test
  public void shouldRestoreStrayDefinitionsAcrossTenantsAndProcessIds() {
    // given - two stray definitions with different process ids and tenants
    final var tenant1 = "tenant-1";
    final var tenant2 = "tenant-2";
    final var v1 = deploy(simpleProcess(Strings.newRandomValidBpmnId()), tenant1);
    final var v2 = deploy(simpleProcess(Strings.newRandomValidBpmnId()), tenant2);
    injectPendingDeletion(v1);
    injectPendingDeletion(v2);

    // when
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();

    // then - both are flipped back to ACTIVE, and the cursor loop terminates cleanly
    final var finished =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
            .getFirst();
    assertThat(finished.getValue().getRestoredCount()).isEqualTo(2);

    final var restoredKeys =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.RESTORED)
            .limit(2)
            .asList()
            .stream()
            .map(r -> r.getValue().getProcessDefinitionKey())
            .toList();
    assertThat(restoredKeys)
        .containsExactlyInAnyOrder(v1.getProcessDefinitionKey(), v2.getProcessDefinitionKey());

    final var continueCommands =
        RecordingExporter.processDefinitionRestoreRecords()
            .limit(r -> r.getIntent() == ProcessDefinitionRestoreIntent.FINISHED)
            .asList()
            .stream()
            .filter(r -> r.getIntent() == ProcessDefinitionRestoreIntent.CONTINUE)
            .toList();
    // each definition took its own CONTINUE, plus the one that found the end
    assertThat(continueCommands).hasSize(3);
  }

  @Test
  public void shouldNotTouchDrainingOrActiveDefinitions() {
    // given - a DRAINING definition and a healthy ACTIVE one alongside the stray
    final var drainingId = Strings.newRandomValidBpmnId();
    final var activeId = Strings.newRandomValidBpmnId();
    final var strayId = Strings.newRandomValidBpmnId();
    final var draining = deploy(simpleProcess(drainingId));
    final var active = deploy(simpleProcess(activeId));
    final var stray = deploy(simpleProcess(strayId));

    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .key(draining.getProcessDefinitionKey())
            .process(
                ProcessIntent.DRAINING,
                new ProcessRecord()
                    .setKey(draining.getProcessDefinitionKey())
                    .setBpmnProcessId(draining.getBpmnProcessId())
                    .setVersion(draining.getVersion())
                    .setResourceName(draining.getResourceName())
                    .setTenantId(draining.getTenantId())),
        RecordToWrite.event()
            .key(stray.getProcessDefinitionKey())
            .process(
                ProcessIntent.DELETING,
                new ProcessRecord()
                    .setKey(stray.getProcessDefinitionKey())
                    .setBpmnProcessId(stray.getBpmnProcessId())
                    .setVersion(stray.getVersion())
                    .setResourceName(stray.getResourceName())
                    .setTenantId(stray.getTenantId())));
    engine.start();

    // when
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();

    // then - only the stray is restored
    awaitRestoreFinishedAndCommitted();
    final var restoredKeys =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.RESTORED)
            .limit(1)
            .asList()
            .stream()
            .map(r -> r.getValue().getProcessDefinitionKey())
            .toList();
    assertThat(restoredKeys).containsExactly(stray.getProcessDefinitionKey());

    final var processState = engine.getProcessingState().getProcessState();
    assertThat(
            processState
                .getProcessByKeyAndTenant(
                    draining.getProcessDefinitionKey(), draining.getTenantId())
                .getState())
        .isEqualTo(PersistedProcessState.DRAINING);
    assertThat(
            processState
                .getProcessByKeyAndTenant(active.getProcessDefinitionKey(), active.getTenantId())
                .getState())
        .isEqualTo(PersistedProcessState.ACTIVE);
  }

  @Test
  public void shouldNotRunRestoreAgainAfterRestartAndShouldSurviveMidChainRestart() {
    // given - a stray definition, restore enabled, and the cleanup already finished once
    final var v1 = deploy(simpleProcess(Strings.newRandomValidBpmnId()));
    injectPendingDeletion(v1);

    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();

    final var finishedRecord =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
            .getFirst();

    // when - restart again; the partition-local flag must prevent a second run
    engine.stop();
    engine.start();

    // trigger a marker deployment so we know the engine is running past the restore window
    final var markerKey =
        engine
            .deployment()
            .withXmlResource(simpleProcess(Strings.newRandomValidBpmnId()))
            .deploy()
            .getKey();
    final var markerRecord =
        RecordingExporter.deploymentRecords(
                io.camunda.zeebe.protocol.record.intent.DeploymentIntent.CREATED)
            .filter(r -> r.getKey() == markerKey)
            .getFirst();

    // then - no new PROCESS_DEFINITION_RESTORE records were written after the FINISHED event
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= markerRecord.getPosition())
                .withValueType(
                    io.camunda.zeebe.protocol.record.ValueType.PROCESS_DEFINITION_RESTORE)
                .asList()
                .stream()
                .filter(r -> r.getPosition() > finishedRecord.getPosition())
                .toList())
        .isEmpty();

    // and - a broker crash can leave a CONTINUE command committed but unprocessed mid-chain (the
    // one-time flag is already set by the earlier STARTED event, so recovery must resume the
    // in-log command rather than relying on the trigger to restart the chain)
    final var alreadyVisited = deploy(simpleProcess(Strings.newRandomValidBpmnId()));
    final var secondStray = deploy(simpleProcess(Strings.newRandomValidBpmnId()));
    injectPendingDeletion(secondStray);

    engine.stop();
    engine.writeRecords(
        RecordToWrite.command()
            .processDefinitionRestore(
                ProcessDefinitionRestoreIntent.CONTINUE,
                new ProcessDefinitionRestoreRecord()
                    .setTenantId(alreadyVisited.getTenantId())
                    .setProcessDefinitionKey(alreadyVisited.getProcessDefinitionKey())));
    engine.start();

    // then - recovery processes the in-log CONTINUE, finds the stray beyond the cursor, and
    // finishes the chain without the trigger re-issuing a new START
    final var secondFinished =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
            .skip(1)
            .getFirst();
    final var secondRestored =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.RESTORED)
            .withRecordKey(secondStray.getProcessDefinitionKey())
            .getFirst();
    assertThat(secondRestored.getValue().getProcessDefinitionKey())
        .isEqualTo(secondStray.getProcessDefinitionKey());
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= secondFinished.getPosition())
                .withValueType(
                    io.camunda.zeebe.protocol.record.ValueType.PROCESS_DEFINITION_RESTORE)
                .asList()
                .stream()
                .filter(
                    r ->
                        r.getPosition() > markerRecord.getPosition()
                            && r.getIntent() == ProcessDefinitionRestoreIntent.START)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldRejectCommittedStartWhenRestoreAlreadyRan() {
    // given - the cleanup already ran once, so the partition-local flag is set
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();
    final var finishedRecord =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
            .getFirst();

    // when - a START committed before a leader change is still unprocessed on recovery
    engine.stop();
    engine.writeRecords(
        RecordToWrite.command()
            .processDefinitionRestore(
                ProcessDefinitionRestoreIntent.START, new ProcessDefinitionRestoreRecord()));
    engine.start();

    final var markerKey =
        engine
            .deployment()
            .withXmlResource(simpleProcess(Strings.newRandomValidBpmnId()))
            .deploy()
            .getKey();
    final var markerRecord =
        RecordingExporter.deploymentRecords(
                io.camunda.zeebe.protocol.record.intent.DeploymentIntent.CREATED)
            .filter(r -> r.getKey() == markerKey)
            .getFirst();

    // then - the redundant START is rejected, so the run is not started a second time
    final var rejection =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.START)
            .onlyCommandRejections()
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= markerRecord.getPosition())
                .withValueType(
                    io.camunda.zeebe.protocol.record.ValueType.PROCESS_DEFINITION_RESTORE)
                .asList()
                .stream()
                .filter(
                    r ->
                        r.getPosition() > finishedRecord.getPosition()
                            && r.getIntent() == ProcessDefinitionRestoreIntent.STARTED)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldNotScheduleRestoreWhenFlagDisabled() {
    // given - a stray definition, restore left at its EngineRule-forced default (disabled)
    final var v1 = deploy(simpleProcess(Strings.newRandomValidBpmnId()));
    injectPendingDeletion(v1);

    // when - restart without enabling the flag
    engine.stop();
    engine.start();

    // trigger a marker deployment so the stream has moved forward
    final var markerKey =
        engine
            .deployment()
            .withXmlResource(simpleProcess(Strings.newRandomValidBpmnId()))
            .deploy()
            .getKey();
    final var markerRecord =
        RecordingExporter.deploymentRecords(
                io.camunda.zeebe.protocol.record.intent.DeploymentIntent.CREATED)
            .filter(r -> r.getKey() == markerKey)
            .getFirst();

    awaitCommitted(markerRecord);

    // then - no PROCESS_DEFINITION_RESTORE records were ever written
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= markerRecord.getPosition())
                .withValueType(
                    io.camunda.zeebe.protocol.record.ValueType.PROCESS_DEFINITION_RESTORE)
                .asList())
        .isEmpty();

    // and the stray row is left untouched
    assertThat(
            engine
                .getProcessingState()
                .getProcessState()
                .getProcessByKeyAndTenant(v1.getProcessDefinitionKey(), v1.getTenantId())
                .getState())
        .isEqualTo(PersistedProcessState.PENDING_DELETION);
  }

  @Test
  public void shouldVisitOneDefinitionPerContinueAndStillFindAStrayNearTheEnd() {
    // given - several ACTIVE definitions with the stray deployed last
    final var definitions = 5;
    IntStream.range(0, definitions - 1)
        .forEach(i -> deploy(simpleProcess(Strings.newRandomValidBpmnId())));
    final var stray = deploy(simpleProcess(Strings.newRandomValidBpmnId()));
    injectPendingDeletion(stray);

    // when
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();

    // then - the stray near the end is still found and restored
    final var finishedRecord =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
            .getFirst();
    final var restored =
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.RESTORED)
            .getFirst();
    assertThat(restored.getValue().getProcessDefinitionKey())
        .isEqualTo(stray.getProcessDefinitionKey());

    // and - each definition took its own CONTINUE, plus the one that found the end
    final var continueCommands =
        RecordingExporter.processDefinitionRestoreRecords()
            .limit(r -> r.getPosition() >= finishedRecord.getPosition())
            .asList()
            .stream()
            .filter(r -> r.getIntent() == ProcessDefinitionRestoreIntent.CONTINUE)
            .toList();
    assertThat(continueCommands).hasSize(definitions + 1);
  }

  @Test
  public void shouldResolveRestoredDefinitionByIdAndVersionAfterCacheReload() {
    // given - the stray is the only (and therefore latest) version of its process id
    final var v1 = deploy(simpleProcess(Strings.newRandomValidBpmnId()));
    injectPendingDeletion(v1);

    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();
    awaitRestoreFinishedAndCommitted();

    // when - the in-memory cache is cleared, forcing a reload through the by-version lookup path
    final var processState = engine.getProcessingState().getProcessState();
    processState.clearCache();
    final var byVersion =
        processState.getProcessByProcessIdAndVersion(
            io.camunda.zeebe.util.buffer.BufferUtil.wrapString(v1.getBpmnProcessId()),
            v1.getVersion(),
            v1.getTenantId());

    // then - the by-version reader resolves through to the restored by-key row
    assertThat(byVersion.getState()).isEqualTo(PersistedProcessState.ACTIVE);
  }

  @Test
  public void shouldResolveLatestAndAcceptInstanceCreationAfterRestore() {
    // given - the stray is the latest version of its process id
    final var processId = Strings.newRandomValidBpmnId();
    final var v1 = deploy(simpleProcess(processId));
    injectPendingDeletion(v1);

    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableProcessDefinitionRestore(true));
    engine.start();
    awaitRestoreFinishedAndCommitted();

    // when - "latest" is resolved and an instance is created
    final var latest =
        engine
            .getProcessingState()
            .getProcessState()
            .getLatestProcessVersionByProcessId(
                io.camunda.zeebe.util.buffer.BufferUtil.wrapString(processId), v1.getTenantId());
    final var instanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // then - latest still resolves to the (now ACTIVE again) only version, and instance creation,
    // already allowed against a resting PENDING_DELETION definition, keeps working
    assertThat(latest.getKey()).isEqualTo(v1.getProcessDefinitionKey());
    final var started =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withProcessInstanceKey(instanceKey)
            .getFirst();
    assertThat(started.getValue().getProcessDefinitionKey())
        .isEqualTo(v1.getProcessDefinitionKey());
  }

  /**
   * Records are exported before the state transaction commits, so reading the state right after
   * seeing a record races with the stream processor's own transaction.
   */
  private void awaitRestoreFinishedAndCommitted() {
    awaitCommitted(
        RecordingExporter.processDefinitionRestoreRecords(ProcessDefinitionRestoreIntent.FINISHED)
            .getFirst());
  }

  private void awaitCommitted(final Record<?> record) {
    Awaitility.await("Await the command that produced the record to be fully processed")
        .untilAsserted(
            () ->
                assertThat(engine.getLastProcessedPosition())
                    .isGreaterThanOrEqualTo(record.getSourceRecordPosition()));
  }

  private static BpmnModelInstance simpleProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
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

  private ProcessMetadataValue deploy(final BpmnModelInstance model, final String tenantId) {
    return engine
        .deployment()
        .withXmlResource(model)
        .withTenantId(tenantId)
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  /**
   * Seeds a resting {@code PENDING_DELETION} by writing a standalone {@code DELETING} event: its
   * applier sets the state without the {@code DELETED} that normally follows atomically,
   * reproducing the corrupted rest state that a real deletion cannot leave behind.
   */
  private void injectPendingDeletion(final ProcessMetadataValue metadata) {
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .key(metadata.getProcessDefinitionKey())
            .process(
                ProcessIntent.DELETING,
                new ProcessRecord()
                    .setKey(metadata.getProcessDefinitionKey())
                    .setBpmnProcessId(metadata.getBpmnProcessId())
                    .setVersion(metadata.getVersion())
                    .setResourceName(metadata.getResourceName())
                    .setTenantId(metadata.getTenantId())));
    engine.start();

    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
  }
}
