/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Engine-level test of the RESOLUTION_FAIL chain. The processor unit test mocks the state writer,
 * so it never exercises the eager application of RESOLUTION_FAILED: the event removes the pending
 * marker before the BATCH_CREATE_INCIDENTS command it was appended with is processed, and the drain
 * must still proceed (the drain processors deliberately have no isPending guard).
 */
public final class SecretReferenceResolutionFailTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final int BATCH_SIZE = 100;
  private static final int JOB_COUNT = 150;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateIncidentsForAllWaitingJobsOnFailedResolution() {
    // given - more jobs waiting on the secret reference than fit in one batch
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final var storeId = "store-" + Strings.newRandomValidBpmnId();
    final var secretReference = "secret-" + Strings.newRandomValidBpmnId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    IntStream.range(0, JOB_COUNT)
        .forEach(i -> ENGINE.processInstance().ofBpmnProcessId(processId).create());

    final List<Long> jobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .limit(JOB_COUNT)
            .map(Record::getKey)
            .toList();

    final var requestedRecord =
        new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
    jobKeys.forEach(requestedRecord::addJobKey);
    final var resolutionFailRecord =
        new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);

    // when - the waiting state is seeded on replay and the failure is processed
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, requestedRecord),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.RESOLUTION_FAIL, resolutionFailRecord));
    // starting the engine re-exports the whole partition log, so clear the previously seen records
    RecordingExporter.reset();
    ENGINE.start();

    // then - every waiting job receives exactly one incident
    final var incidents =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
            .withBpmnProcessId(processId)
            .limit(JOB_COUNT)
            .asList();
    assertThat(incidents)
        .extracting(record -> record.getValue().getJobKey())
        .containsExactlyInAnyOrderElementsOf(jobKeys);

    // and - the failure is recorded once even though the drain spans several commands
    final var secretRecords =
        RecordingExporter.secretReferenceRecords()
            .withStoreId(storeId)
            .withSecretReference(secretReference)
            .limitByCount(
                record -> record.getIntent() == SecretReferenceIntent.BATCH_INCIDENTS_CREATED, 2)
            .asList();
    assertThat(secretRecords)
        .filteredOn(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_FAILED)
        .hasSize(1);

    // and - the drain runs after the pending marker is gone, in two batches
    final var batchEvents =
        secretRecords.stream()
            .filter(record -> record.getIntent() == SecretReferenceIntent.BATCH_INCIDENTS_CREATED)
            .toList();
    assertThat(batchEvents.get(0).getValue().getJobKeys())
        .containsExactlyElementsOf(jobKeys.subList(0, BATCH_SIZE));
    assertThat(batchEvents.get(1).getValue().getJobKeys())
        .containsExactlyElementsOf(jobKeys.subList(BATCH_SIZE, JOB_COUNT));
  }

  @Test
  public void shouldRejectResolutionFailWhenReferenceNotPending() {
    // given - resolution was never requested for this secret reference
    final var storeId = "store-" + Strings.newRandomValidBpmnId();
    final var secretReference = "secret-" + Strings.newRandomValidBpmnId();
    final var resolutionFailRecord =
        new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
    // a second, equally unknown reference: its rejection bounds the window in which a follow-up
    // record of the first command would have to appear
    final var sentinelFailRecord =
        new SecretReferenceRecord()
            .setStoreId(storeId)
            .setSecretReference("sentinel-" + Strings.newRandomValidBpmnId());

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.RESOLUTION_FAIL, resolutionFailRecord),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.RESOLUTION_FAIL, sentinelFailRecord));

    // then - the command is rejected
    final var records =
        RecordingExporter.secretReferenceRecords()
            .withStoreId(storeId)
            .limitByCount(record -> record.getRecordType() == RecordType.COMMAND_REJECTION, 2)
            .asList();
    final var rejection =
        records.stream()
            .filter(record -> record.getRecordType() == RecordType.COMMAND_REJECTION)
            .findFirst()
            .orElseThrow();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(secretReference).contains(storeId);

    // and - neither the failure event nor the drain command is written
    assertThat(records)
        .extracting(Record::getIntent)
        .doesNotContain(
            SecretReferenceIntent.RESOLUTION_FAILED, SecretReferenceIntent.BATCH_CREATE_INCIDENTS);
  }

  @Test
  public void shouldRejectRedundantResolutionFail() {
    // given - a pending secret reference with no jobs waiting on it
    final var storeId = "store-" + Strings.newRandomValidBpmnId();
    final var secretReference = "secret-" + Strings.newRandomValidBpmnId();
    final var requestedRecord =
        new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
    final var resolutionFailRecord =
        new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);

    // when - the same failure is reported twice
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, requestedRecord),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.RESOLUTION_FAIL, resolutionFailRecord),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.RESOLUTION_FAIL, resolutionFailRecord));
    // starting the engine re-exports the whole partition log, so clear the previously seen records
    RecordingExporter.reset();
    ENGINE.start();

    // then - the applied event cleared the pending marker, so the repeat finds nothing to fail
    final var records =
        RecordingExporter.secretReferenceRecords()
            .withStoreId(storeId)
            .withSecretReference(secretReference)
            .limit(record -> record.getRecordType() == RecordType.COMMAND_REJECTION)
            .asList();
    assertThat(records)
        .filteredOn(record -> record.getIntent() == SecretReferenceIntent.RESOLUTION_FAILED)
        .hasSize(1);

    final var rejection = records.get(records.size() - 1);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(secretReference).contains(storeId);
  }

  @Test
  public void shouldPropagateResolutionStateToFailedEvent() {
    // given - the scheduler reports why resolution failed
    final var storeId = "store-" + Strings.newRandomValidBpmnId();
    final var secretReference = "secret-" + Strings.newRandomValidBpmnId();
    final var requestedRecord =
        new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
    final var resolutionFailRecord =
        new SecretReferenceRecord()
            .setStoreId(storeId)
            .setSecretReference(secretReference)
            .setResolutionState(ResolutionState.STORE_UNAVAILABLE);

    // when
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, requestedRecord),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.RESOLUTION_FAIL, resolutionFailRecord));
    // starting the engine re-exports the whole partition log, so clear the previously seen records
    RecordingExporter.reset();
    ENGINE.start();

    // then - the reason survives onto the exported event
    final var failedEvent =
        RecordingExporter.secretReferenceRecords(SecretReferenceIntent.RESOLUTION_FAILED)
            .withStoreId(storeId)
            .withSecretReference(secretReference)
            .getFirst();
    assertThat(failedEvent.getValue().getResolutionState())
        .isEqualTo(ResolutionState.STORE_UNAVAILABLE);
  }
}
