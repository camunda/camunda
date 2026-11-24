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

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * The deployment partition suspends and resumes the versions, then distributes the command so that
 * every partition reaches the same state. The engine tests use JUnit 4 because {@link EngineRule}
 * has no JUnit 5 counterpart.
 */
public class ProcessDefinitionSuspensionMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;
  private static final int OTHER_PARTITION = 2;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldSuspendOnAllPartitions() {
    // given
    final long processDefinitionKey = deploy(Strings.newRandomValidBpmnId());

    // when
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend();

    // then
    assertDistributionFinished(suspended);
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .between(
                      r -> r.getIntent() == ProcessDefinitionSuspensionIntent.SUSPEND,
                      r -> r.getIntent() == ProcessDefinitionSuspensionIntent.SUSPENDED))
          .extracting(Record::getIntent, Record::getRecordType)
          .containsExactly(
              tuple(ProcessDefinitionSuspensionIntent.SUSPEND, RecordType.COMMAND),
              tuple(ProcessIntent.SUSPENDED, RecordType.EVENT),
              tuple(ProcessDefinitionSuspensionIntent.SUSPENDED, RecordType.EVENT));
      assertThat(
              RecordingExporter.processRecords(ProcessIntent.SUSPENDED)
                  .withPartitionId(partitionId)
                  .getFirst()
                  .getValue())
          .extracting(Process::getProcessDefinitionKey)
          .isEqualTo(processDefinitionKey);
    }
  }

  @Test
  public void shouldResumeOnAllPartitions() {
    // given
    final long processDefinitionKey = deploy(Strings.newRandomValidBpmnId());
    assertDistributionFinished(
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend());

    // when
    final var resumed =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .resume();

    // then
    assertDistributionFinished(resumed);
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.processRecords(ProcessIntent.RESUMED)
                  .withPartitionId(partitionId)
                  .getFirst()
                  .getValue())
          .extracting(Process::getProcessDefinitionKey)
          .isEqualTo(processDefinitionKey);
    }
  }

  @Test
  public void shouldOnlySuspendTheVersionsTheDeploymentPartitionSuspended() {
    // given - two versions, the first one already suspended
    final var processId = Strings.newRandomValidBpmnId();
    final long firstVersionKey = deploy(processId);
    final long secondVersionKey = deploy(processId, "v2");
    assertDistributionFinished(
        engine.processDefinitionSuspension().withProcessDefinitionKey(firstVersionKey).suspend());

    // when
    final var suspended =
        engine.processDefinitionSuspension().withBpmnProcessId(processId).suspend();

    // then - the other partitions only suspend the second version, with the same outcomes
    assertDistributionFinished(suspended);
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.processRecords(ProcessIntent.SUSPENDED)
                  .withPartitionId(partitionId)
                  .limit(2))
          .extracting(r -> r.getValue().getProcessDefinitionKey())
          .containsExactly(firstVersionKey, secondVersionKey);
      assertThat(requestEventOnPartition(suspended, partitionId).getValue().getResolvedVersions())
          .extracting(
              ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
              ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
          .containsExactly(
              tuple(firstVersionKey, ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED),
              tuple(secondVersionKey, ProcessDefinitionSuspensionOutcome.SUSPENDED));
    }
  }

  @Test
  public void shouldNotDistributeWhenNoVersionChanges() {
    // given
    final long processDefinitionKey = deploy(Strings.newRandomValidBpmnId());
    assertDistributionFinished(
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend());

    // when - suspended again, and resumed to have a record to wait for
    final var suspendedAgain =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend();
    final var resumed =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .resume();
    assertDistributionFinished(resumed);

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.STARTED)
                .withDistributionValueType(ValueType.PROCESS_DEFINITION_SUSPENSION)
                .limit(r -> r.getKey() == resumed.getKey()))
        .extracting(Record::getKey)
        .doesNotContain(suspendedAgain.getKey())
        .hasSize(2);
  }

  @Test
  public void shouldApplySuspendAndResumeInOrderWhenTheSuspensionIsSentAgain() {
    // given - the first suspension sent to the other partition is lost
    final long processDefinitionKey = deploy(Strings.newRandomValidBpmnId());
    final var dropped = new AtomicBoolean();
    engine.interceptInterPartitionCommands(
        (receiver, valueType, intent, key, command) ->
            receiver != OTHER_PARTITION
                || valueType != ValueType.PROCESS_DEFINITION_SUSPENSION
                || !dropped.compareAndSet(false, true));

    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend();
    final var resumed =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .resume();

    // when
    final var applied =
        resendUntilAppliedOnOtherPartition(List.of(suspended.getKey(), resumed.getKey()));

    // then - the resume waited for the suspension it came after
    assertThat(applied)
        .extracting(Record::getKey, Record::getIntent)
        .containsExactly(
            tuple(suspended.getKey(), ProcessDefinitionSuspensionIntent.SUSPENDED),
            tuple(resumed.getKey(), ProcessDefinitionSuspensionIntent.RESUMED));
    assertThat(
            RecordingExporter.processRecords()
                .onlyEvents()
                .withPartitionId(OTHER_PARTITION)
                .withProcessDefinitionKey(processDefinitionKey)
                .filter(
                    r ->
                        r.getIntent() == ProcessIntent.SUSPENDED
                            || r.getIntent() == ProcessIntent.RESUMED)
                .limit(2))
        .extracting(Record::getIntent)
        .containsExactly(ProcessIntent.SUSPENDED, ProcessIntent.RESUMED);
  }

  @Test
  public void shouldSuspendOnOtherPartitionAfterRestart() {
    // given - the suspension doesn't reach the other partition before the restart
    final long processDefinitionKey = deploy(Strings.newRandomValidBpmnId());
    engine.interceptInterPartitionCommands(
        (receiver, valueType, intent, key, command) ->
            receiver != OTHER_PARTITION || valueType != ValueType.PROCESS_DEFINITION_SUSPENSION);
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend();
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.DISTRIBUTING)
        .withPartitionId(1)
        .withRecordKey(suspended.getKey())
        .filter(r -> r.getValue().getPartitionId() == OTHER_PARTITION)
        .await();

    // when - the restart also stops dropping the command
    engine.stop();
    engine.start();

    // then
    assertThat(resendUntilAppliedOnOtherPartition(List.of(suspended.getKey())))
        .extracting(Record::getIntent)
        .containsExactly(ProcessDefinitionSuspensionIntent.SUSPENDED);
    assertThat(
            RecordingExporter.processRecords(ProcessIntent.SUSPENDED)
                .withPartitionId(OTHER_PARTITION)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();
  }

  private long deploy(final String processId) {
    return deploy(processId, "task");
  }

  /** A different element ID makes a new version of the same process. */
  private long deploy(final String processId, final String elementId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId).startEvent().task(elementId).endEvent().done();
    return engine
        .deployment()
        .withXmlResource(process)
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  /** The request event another partition wrote when it applied the distributed command. */
  private static Record<ProcessDefinitionSuspensionRecordValue> requestEventOnPartition(
      final Record<ProcessDefinitionSuspensionRecordValue> requestEvent, final int partitionId) {
    return RecordingExporter.processDefinitionSuspensionRecords()
        .onlyEvents()
        .withPartitionId(partitionId)
        .withRecordKey(requestEvent.getKey())
        .getFirst();
  }

  private static void assertDistributionFinished(final Record<?> requestEvent) {
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .withPartitionId(1)
                .withRecordKey(requestEvent.getKey())
                .exists())
        .describedAs("the distribution of %s finished", requestEvent.getIntent())
        .isTrue();
  }

  /**
   * Moves time forward until the other partition has applied the distributed commands with the
   * given keys, so that a lost command is sent again. Returns the request events of the other
   * partition, in the order it applied them.
   */
  private List<Record<ProcessDefinitionSuspensionRecordValue>> resendUntilAppliedOnOtherPartition(
      final List<Long> keys) {
    RecordingExporter.await()
        .untilAsserted(
            () -> {
              engine
                  .getClock()
                  .addTime(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL);
              assertThat(appliedOnOtherPartition(keys)).hasSameSizeAs(keys);
            });
    return appliedOnOtherPartition(keys);
  }

  private static List<Record<ProcessDefinitionSuspensionRecordValue>> appliedOnOtherPartition(
      final List<Long> keys) {
    return RecordingExporter.processDefinitionSuspensionRecords()
        .onlyEvents()
        .withPartitionId(OTHER_PARTITION)
        .filter(event -> keys.contains(event.getKey()))
        .limit(keys.size())
        .toList();
  }
}
