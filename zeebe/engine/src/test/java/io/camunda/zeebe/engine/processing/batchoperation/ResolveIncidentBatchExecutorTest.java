/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class ResolveIncidentBatchExecutorTest extends AbstractBatchOperationTest {

  private static final String JOB_TYPE = "test";

  @Test
  public void shouldResolveJobIncident() {
    // create a process with a failed job
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "failingTask", t -> t.zeebeJobType(JOB_TYPE).zeebeInputExpression("foo", "foo"))
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Maps.of(entry("foo", "bar")))
            .create();

    // wait for the job to exist
    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();

    engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(1).activate();

    // fail the job
    final Record<JobRecordValue> failedEvent =
        engine.job().withType(JOB_TYPE).ofInstance(processInstanceKey).withRetries(0).fail();

    final var incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withJobKey(failedEvent.getKey())
            .getFirst()
            .getKey();

    final var batchOperationKey =
        createNewResolveIncidentsBatchOperation(Map.of(processInstanceKey, Set.of(incidentKey)));

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and a follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands()
                .limit(r -> r.getIntent() == BatchOperationExecutionIntent.EXECUTE))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.EXECUTE);

    // and we have a job retry command and a resolve incident command
    assertThat(
            RecordingExporter.jobRecords()
                .withRecordKey(failedEvent.getKey())
                .limit(r -> r.getIntent() == JobIntent.UPDATE_RETRIES))
        .extracting(Record::getIntent)
        .containsSequence(JobIntent.UPDATE_RETRIES);

    final var incidentCommand =
        RecordingExporter.incidentRecords()
            .withRecordType(RecordType.COMMAND)
            .withRecordKey(incidentKey)
            .getFirst();
    assertThat(incidentCommand.getIntent()).isEqualTo(IncidentIntent.RESOLVE);
    assertThat(incidentCommand.getAuthorizations())
        .isEqualTo(Map.of(AUTHORIZED_USERNAME, OTHER_USER.getUsername()));
    assertThat(incidentCommand.getBatchOperationReference()).isEqualTo(batchOperationKey);
  }

  @Test
  public void shouldResolveNonJobIncident() {
    // create a process with a failed job
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .intermediateCatchEvent(
                    "catch",
                    e -> e.message(m -> m.name("cancel").zeebeCorrelationKeyExpression("orderId")))
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();

    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId("process").withVariable("orderId", true).create();

    final var incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    final var batchOperationKey =
        createNewResolveIncidentsBatchOperation(Map.of(processInstanceKey, Set.of(incidentKey)));

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and a follow op up command to execute again
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyCommands()
                .limit(r -> r.getIntent() == BatchOperationExecutionIntent.EXECUTE))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.EXECUTE);

    // and we have a resolved the incident
    assertThat(
            RecordingExporter.incidentRecords()
                .withRecordKey(incidentKey)
                .withIntents(IncidentIntent.RESOLVE, IncidentIntent.RESOLVED)
                .limit(r -> r.getIntent() == IncidentIntent.RESOLVED))
        .isNotEmpty()
        .extracting(Record::getIntent, Record::getBatchOperationReference)
        .containsExactly(
            tuple(IncidentIntent.RESOLVE, batchOperationKey),
            tuple(IncidentIntent.RESOLVED, batchOperationKey));
  }

  @Test
  public void shouldRejectNonExistingIncident() {
    // some random keys
    final var processInstanceKey = 42L;
    final var incidentKey = 43L;

    final var batchOperationKey =
        createNewResolveIncidentsBatchOperation(Map.of(processInstanceKey, Set.of(incidentKey)));

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and we have a rejected incident resolve command
    assertThat(
            RecordingExporter.incidentRecords()
                .onlyCommandRejections()
                .withRecordKey(incidentKey)
                .withIntents(IncidentIntent.RESOLVE)
                .limit(r -> r.getIntent() == IncidentIntent.RESOLVE))
        .allSatisfy(r -> assertThat(r.getBatchOperationReference()).isEqualTo(batchOperationKey));
  }
}
