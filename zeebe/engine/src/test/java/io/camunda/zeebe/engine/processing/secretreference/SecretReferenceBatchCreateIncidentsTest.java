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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Engine-level test of the BATCH_CREATE_INCIDENTS chain. Unlike the processor unit test, this
 * exercises the eager application of the BATCH_INCIDENTS_CREATED event between batches: the applied
 * event must remove the processed waiting entries before the next batch is collected, otherwise the
 * chain would re-batch the same job keys forever.
 */
public final class SecretReferenceBatchCreateIncidentsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final int BATCH_SIZE = 100;
  private static final int JOB_COUNT = 150;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDrainAllWaitingJobsAcrossChainedBatches() {
    // given - more jobs waiting on the same secret reference than fit in one batch
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
    final var firstBatch =
        new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
    jobKeys.subList(0, BATCH_SIZE).forEach(firstBatch::addJobKey);

    // when - the waiting state is seeded on replay and the first batch command is processed
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, requestedRecord),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.BATCH_CREATE_INCIDENTS, firstBatch));
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
    assertThat(incidents)
        .allSatisfy(
            record -> {
              assertThat(record.getValue().getErrorMessage())
                  .contains(secretReference)
                  .contains(storeId);
            });

    // and - the chain drains the waiting jobs in two batches
    final var batchEvents =
        RecordingExporter.secretReferenceRecords()
            .withStoreId(storeId)
            .withSecretReference(secretReference)
            .withIntent(SecretReferenceIntent.BATCH_INCIDENTS_CREATED)
            .limit(2)
            .asList();
    assertThat(batchEvents.get(0).getValue().getJobKeys())
        .containsExactlyElementsOf(jobKeys.subList(0, BATCH_SIZE));
    assertThat(batchEvents.get(1).getValue().getJobKeys())
        .containsExactlyElementsOf(jobKeys.subList(BATCH_SIZE, JOB_COUNT));

    // and - the follow-up command re-batches exactly the remaining jobs
    final var commands =
        RecordingExporter.secretReferenceRecords()
            .withStoreId(storeId)
            .withSecretReference(secretReference)
            .withIntent(SecretReferenceIntent.BATCH_CREATE_INCIDENTS)
            .onlyCommands()
            .limit(2)
            .asList();
    assertThat(commands.get(1).getValue().getJobKeys())
        .containsExactlyElementsOf(jobKeys.subList(BATCH_SIZE, JOB_COUNT));
  }

  @Test
  public void shouldNotCreateIncidentForJobAlreadyDrainedByReactivation() {
    // given - one job waiting on the secret reference, and a reactivation chain that drains it
    //         before the incident command carrying the same job key is processed. The command was
    //         written when the job was still waiting, so its key list is stale by then.
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
    ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst().getKey();

    final var waiting =
        new SecretReferenceRecord()
            .setStoreId(storeId)
            .setSecretReference(secretReference)
            .addJobKey(jobKey);
    final var reactivated =
        new SecretReferenceRecord()
            .setStoreId(storeId)
            .setSecretReference(secretReference)
            .addJobKey(jobKey);
    final var staleBatch =
        new SecretReferenceRecord()
            .setStoreId(storeId)
            .setSecretReference(secretReference)
            .addJobKey(jobKey);

    // when - the reactivation is applied before the stale incident command is processed
    ENGINE.stop();
    ENGINE.writeRecords(
        RecordToWrite.event().secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, waiting),
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.BATCH_JOBS_REACTIVATED, reactivated),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.BATCH_CREATE_INCIDENTS, staleBatch));
    // starting the engine re-exports the whole partition log, so clear the previously seen records
    RecordingExporter.reset();
    ENGINE.start();

    // then - the job is not incidented, because it is no longer waiting on the reference. The
    //        bound is pinned to this test's own store so a re-exported record cannot end it early.
    final var incidents =
        RecordingExporter.records()
            .limit(
                record ->
                    record.getIntent() == SecretReferenceIntent.BATCH_INCIDENTS_CREATED
                        && record.getValue() instanceof final SecretReferenceRecordValue value
                        && value.getStoreId().equals(storeId))
            .incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withBpmnProcessId(processId)
            .asList();
    assertThat(incidents).isEmpty();

    // and - the batch event still carries the key, so the waiting entry is cleaned up either way
    final var batchEvent =
        RecordingExporter.secretReferenceRecords()
            .withStoreId(storeId)
            .withSecretReference(secretReference)
            .withIntent(SecretReferenceIntent.BATCH_INCIDENTS_CREATED)
            .getFirst();
    assertThat(batchEvent.getValue().getJobKeys()).containsExactly(jobKey);
  }
}
