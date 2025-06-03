/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
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
    // given
    final Map<String, Object> claims = Map.of("claim1", "value1", "claim2", "value2");

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
        createNewResolveIncidentsBatchOperation(
            Map.of(processInstanceKey, Set.of(incidentKey)), claims);

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and we have a job retry command and a resolve incident command
    assertThat(RecordingExporter.jobRecords().withRecordKey(failedEvent.getKey()))
        .extracting(Record::getIntent)
        .containsSequence(JobIntent.UPDATE_RETRIES);

    final var incidentCommands =
        RecordingExporter.incidentRecords()
            .withRecordType(RecordType.COMMAND)
            .withRecordKey(incidentKey)
            .toList();
    assertThat(incidentCommands).hasSize(1);
    assertThat(incidentCommands.getFirst().getIntent()).isEqualTo(IncidentIntent.RESOLVE);
    assertThat(incidentCommands.getFirst().getAuthorizations()).isEqualTo(claims);
  }

  @Test
  public void shouldResolveNonJobIncident() {
    // given
    final Map<String, Object> claims = Map.of("claim1", "value1", "claim2", "value2");

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
        createNewResolveIncidentsBatchOperation(
            Map.of(processInstanceKey, Set.of(incidentKey)), claims);

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents())
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and we have a resolve incident command
    assertThat(RecordingExporter.jobRecords().withProcessInstanceKey(processInstanceKey)).isEmpty();
    assertThat(RecordingExporter.incidentRecords().withRecordKey(incidentKey))
        .extracting(Record::getIntent)
        .containsSequence(IncidentIntent.RESOLVE);
  }
}
