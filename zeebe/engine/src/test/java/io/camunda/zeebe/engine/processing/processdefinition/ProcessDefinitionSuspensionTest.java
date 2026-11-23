/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;

public class ProcessDefinitionSuspensionTest {

  private static final String JOB_TYPE = "task";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSuspendVersionByKey() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processModel(processId, "v1"));
    final var v2 = deploy(processModel(processId, "v2"));

    // when
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v2.getProcessDefinitionKey())
            .suspend();

    // then
    assertThat(suspended.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPENDED);
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getVersion,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(v2.getProcessDefinitionKey(), 2, ProcessDefinitionSuspensionOutcome.SUSPENDED));
    assertThat(processEventsOf(suspended))
        .extracting(Record::getIntent, r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(tuple(ProcessIntent.SUSPENDED, v2.getProcessDefinitionKey()));
  }

  @Test
  public void shouldSuspendVersionByBpmnProcessIdAndVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    deploy(processModel(processId, "v2"));

    // when
    final var suspended =
        engine.processDefinitionSuspension().withBpmnProcessId(processId).withVersion(1).suspend();

    // then
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(v1.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.SUSPENDED));
    assertThat(processEventsOf(suspended))
        .extracting(Record::getIntent, r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(tuple(ProcessIntent.SUSPENDED, v1.getProcessDefinitionKey()));
  }

  @Test
  public void shouldSuspendAllVersionsByBpmnProcessId() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    final var v2 = deploy(processModel(processId, "v2"));

    // when
    final var suspended =
        engine.processDefinitionSuspension().withBpmnProcessId(processId).suspend();

    // then
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getVersion,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(v1.getProcessDefinitionKey(), 1, ProcessDefinitionSuspensionOutcome.SUSPENDED),
            tuple(v2.getProcessDefinitionKey(), 2, ProcessDefinitionSuspensionOutcome.SUSPENDED));
    assertThat(processEventsOf(suspended))
        .extracting(
            Record::getIntent,
            r -> r.getValue().getProcessDefinitionKey(),
            r -> r.getValue().getVersion())
        .containsExactly(
            tuple(ProcessIntent.SUSPENDED, v1.getProcessDefinitionKey(), 1),
            tuple(ProcessIntent.SUSPENDED, v2.getProcessDefinitionKey(), 2));
  }

  @Test
  public void shouldResumeVersionByKey() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));

    // when
    final var resumed =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v1.getProcessDefinitionKey())
            .resume();

    // then
    assertThat(resumed.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.RESUMED);
    assertThat(resumed.getValue().getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(
                v1.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED));
    assertThat(processEventsOf(resumed)).isEmpty();
  }

  @Test
  public void shouldResumeAllVersionsByBpmnProcessId() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    final var v2 = deploy(processModel(processId, "v2"));

    // when
    final var resumed = engine.processDefinitionSuspension().withBpmnProcessId(processId).resume();

    // then
    assertThat(resumed.getValue().getResolvedVersions())
        .extracting(ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey)
        .containsExactly(v1.getProcessDefinitionKey(), v2.getProcessDefinitionKey());
    assertThat(processEventsOf(resumed)).isEmpty();
  }

  @Test
  public void shouldReportAlreadySuspendedVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    suspend(v1);

    // when
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v1.getProcessDefinitionKey())
            .suspend();

    // then
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED);
    assertThat(processEventsOf(suspended)).isEmpty();
  }

  @Test
  public void shouldResumeSuspendedVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    suspend(v1);

    // when
    final var resumed =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v1.getProcessDefinitionKey())
            .resume();

    // then
    assertThat(resumed.getValue().getResolvedVersions())
        .extracting(ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(ProcessDefinitionSuspensionOutcome.RESUMED);
    assertThat(processEventsOf(resumed))
        .extracting(Record::getIntent, r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(tuple(ProcessIntent.RESUMED, v1.getProcessDefinitionKey()));
  }

  @Test
  public void shouldReportOutcomePerVersionWhenSuspendingAllVersions() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    final var v2 = deploy(processModel(processId, "v2"));
    suspend(v1);

    // when
    final var suspended =
        engine.processDefinitionSuspension().withBpmnProcessId(processId).suspend();

    // then
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(
                v1.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED),
            tuple(v2.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.SUSPENDED));
    assertThat(processEventsOf(suspended))
        .extracting(Record::getIntent, r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(tuple(ProcessIntent.SUSPENDED, v2.getProcessDefinitionKey()));
  }

  @Test
  public void shouldReportOutcomePerVersionWhenResumingAllVersions() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    final var v2 = deploy(processModel(processId, "v2"));
    suspend(v2);

    // when
    final var resumed = engine.processDefinitionSuspension().withBpmnProcessId(processId).resume();

    // then
    assertThat(resumed.getValue().getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(v1.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED),
            tuple(v2.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.RESUMED));
    assertThat(processEventsOf(resumed))
        .extracting(Record::getIntent, r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(tuple(ProcessIntent.RESUMED, v2.getProcessDefinitionKey()));
  }

  @Test
  public void shouldSuspendPendingDeletionVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    injectPendingDeletion(v1);

    // when
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v1.getProcessDefinitionKey())
            .suspend();

    // then
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(ProcessDefinitionSuspensionOutcome.SUSPENDED);
    assertThat(processEventsOf(suspended))
        .extracting(Record::getIntent, r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(tuple(ProcessIntent.SUSPENDED, v1.getProcessDefinitionKey()));
  }

  @Test
  public void shouldResumePendingDeletionVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    injectPendingDeletion(v1);
    suspend(v1);

    // when
    final var resumed = engine.processDefinitionSuspension().withBpmnProcessId(processId).resume();

    // then
    assertThat(resumed.getValue().getResolvedVersions())
        .extracting(ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(ProcessDefinitionSuspensionOutcome.RESUMED);
  }

  @Test
  public void shouldRejectDrainingVersionByKey() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    drain(processId, v1);

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v1.getProcessDefinitionKey())
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to suspend process definition with BPMN process ID '%s' and version 1 (key %d), but it is being deleted"
                .formatted(processId, v1.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectDrainingVersionByBpmnProcessIdAndVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    drain(processId, v1);

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(processId)
            .withVersion(1)
            .expectRejection()
            .resume();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to resume process definition with BPMN process ID '%s' and version 1 (key %d), but it is being deleted"
                .formatted(processId, v1.getProcessDefinitionKey()));
  }

  @Test
  public void shouldReportDrainingOutcomeWhenSuspendingAllVersions() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    final var v2 = deploy(processModel(processId, "v2"));
    drain(processId, v1);

    // when
    final var suspended =
        engine.processDefinitionSuspension().withBpmnProcessId(processId).suspend();

    // then
    assertThat(suspended.getValue().getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(v1.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.DRAINING),
            tuple(v2.getProcessDefinitionKey(), ProcessDefinitionSuspensionOutcome.SUSPENDED));
    assertThat(processEventsOf(suspended))
        .extracting(Record::getIntent, r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(tuple(ProcessIntent.SUSPENDED, v2.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectWithoutChangesWhenRecordsExceedBatchSize() {
    // given - every Process event carries the version tag, so a dozen versions with large tags
    // exceed the 4 MB batch limit
    final var processId = helper.getBpmnProcessId();
    final var versions =
        IntStream.rangeClosed(1, 12)
            .mapToObj(
                i ->
                    deploy(
                        Bpmn.createExecutableProcess(processId)
                            .versionTag(i + "x".repeat(400_000))
                            .startEvent()
                            .endEvent()
                            .done()))
            .toList();

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(processId)
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to suspend process definition with BPMN process ID '%s', but the resulting records exceed the maximum batch size. Nothing was changed; suspend the versions one at a time instead"
                .formatted(processId));
    assertThat(rejection.getValue().getResolvedVersions()).isEmpty();

    // and the partition keeps processing: a single version can still be suspended, and its
    // Process event is the only one written
    final var firstKey = versions.getFirst().getProcessDefinitionKey();
    final var suspended =
        engine.processDefinitionSuspension().withProcessDefinitionKey(firstKey).suspend();
    assertThat(suspended.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPENDED);
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= suspended.getPosition())
                .processRecords()
                .withIntent(ProcessIntent.SUSPENDED))
        .extracting(r -> r.getValue().getProcessDefinitionKey())
        .containsExactly(firstKey);

    // and no other version was marked suspended by the rejected command
    final var resumed = engine.processDefinitionSuspension().withBpmnProcessId(processId).resume();
    assertThat(resumed.getValue().getResolvedVersions())
        .extracting(ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            Stream.concat(
                    Stream.of(ProcessDefinitionSuspensionOutcome.RESUMED),
                    Stream.generate(() -> ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED)
                        .limit(versions.size() - 1))
                .toArray(ProcessDefinitionSuspensionOutcome[]::new));
  }

  @Test
  public void shouldRejectUnknownKey() {
    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(123L)
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to suspend process definition with key '123', but no such process definition was found");
  }

  @Test
  public void shouldRejectUnknownBpmnProcessId() {
    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId("unknown")
            .expectRejection()
            .resume();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to resume process definition with BPMN process ID 'unknown', but no such process definition was found");
  }

  @Test
  public void shouldRejectUnknownVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processModel(processId, "v1"));

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(processId)
            .withVersion(2)
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to suspend process definition with BPMN process ID '%s' and version '2', but no such process definition was found"
                .formatted(processId));
  }

  @Test
  public void shouldRejectBpmnProcessIdOfOtherTenant() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processModel(processId, "v1"));

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(processId)
            .withTenantId("other-tenant")
            .withAuthorizedTenantIds("other-tenant")
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to suspend process definition with BPMN process ID '%s', but no such process definition was found"
                .formatted(processId));
  }

  @Test
  public void shouldRejectKeyWithVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));
    deploy(processModel(processId, "v2"));

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v1.getProcessDefinitionKey())
            .withVersion(2)
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to suspend a process definition by key or by BPMN process ID and version, but key '%d' was given with version '2'"
                .formatted(v1.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectVersionZero() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processModel(processId, "v1"));

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(processId)
            .withVersion(0)
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to suspend a process definition with a version of at least 1, or without a version for all versions, but version '0' was given");
  }

  @Test
  public void shouldRejectNegativeVersionOtherThanAllVersions() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processModel(processId, "v1"));

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(processId)
            .withVersion(-2)
            .expectRejection()
            .resume();

    // then - only -1, the unset version, means all versions
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to resume a process definition with a version of at least 1, or without a version for all versions, but version '-2' was given");
  }

  @Test
  public void shouldRejectKeyWithTenant() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(processModel(processId, "v1"));

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(v1.getProcessDefinitionKey())
            .withTenantId("other-tenant")
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to suspend a process definition by key or by BPMN process ID and tenant, but key '%d' was given with tenant 'other-tenant'"
                .formatted(v1.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectWithoutIdentifier() {
    // when
    final var rejection = engine.processDefinitionSuspension().expectRejection().suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to suspend a process definition by key or by BPMN process ID, but none given");
  }

  @Test
  public void shouldRejectWithBothIdentifiers() {
    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(123L)
            .withBpmnProcessId("process")
            .expectRejection()
            .suspend();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to suspend a process definition by key or by BPMN process ID, but both were given: key '123', BPMN process ID 'process'");
  }

  private static BpmnModelInstance processModel(final String processId, final String endEventId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
        .endEvent(endEventId)
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

  /**
   * The per-version {@code Process} events written for the same command, which come before the
   * request event.
   */
  private static List<Record<Process>> processEventsOf(
      final Record<ProcessDefinitionSuspensionRecordValue> requestEvent) {
    return RecordingExporter.records()
        .limit(r -> r.getPosition() >= requestEvent.getPosition())
        .processRecords()
        .withSourceRecordPosition(requestEvent.getSourceRecordPosition())
        .toList();
  }

  private void suspend(final ProcessMetadataValue metadata) {
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .suspend();
  }

  /** A deleted definition keeps draining while it still has a running instance. */
  private void drain(final String processId, final ProcessMetadataValue metadata) {
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).withVersion(1).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    engine.resourceDeletion().withResourceKey(metadata.getProcessDefinitionKey()).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
  }

  /**
   * Seeds a resting {@code PENDING_DELETION} by writing a standalone {@code DELETING} event, the
   * same way {@code PendingDeletionAsActiveTest} does: a real deletion can't leave it behind.
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
