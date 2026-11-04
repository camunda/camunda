/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionSuspensionMultiPartitionTest {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessDefinitionSuspensionMultiPartitionTest.class);
  private static final int PARTITION_COUNT = 3;
  private static final String SUSPENDED_MESSAGE =
      "Expected to create an instance of process with key '%d', but the process definition is suspended";

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private long deploy(final String processId, final boolean messageStart, final String jobType) {
    final var builder = Bpmn.createExecutableProcess(processId).startEvent("start");
    final var model =
        (messageStart ? builder.message(processId) : builder)
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    final long key =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();
    // the process must exist on every partition before the tests act on them
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionValueType(ValueType.DEPLOYMENT)
        .limit(1)
        .await();
    return key;
  }

  private void awaitSuspendedOnAllPartitions(final long eventKey) {
    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      final int p = partition;
      assertThat(
              RecordingExporter.processDefinitionSuspensionRecords(
                      ProcessDefinitionSuspensionIntent.SUSPENDED)
                  .withPartitionId(p)
                  .filter(r -> r.getKey() == eventKey)
                  .exists())
          .describedAs("SUSPENDED on partition %d", p)
          .isTrue();
    }
  }

  @Test
  public void shouldSuspendOnAllPartitions() {
    // given
    final long key = deploy(Strings.newRandomValidBpmnId(), false, "task");

    // when
    final var suspended = engine.processDefinitionSuspension().withKey(key).suspend();

    // then
    awaitSuspendedOnAllPartitions(suspended.getKey());
    for (int partition = 2; partition <= PARTITION_COUNT; partition++) {
      final int p = partition;
      assertThat(
              RecordingExporter.commandDistributionRecords(CommandDistributionIntent.DISTRIBUTING)
                  .withDistributionValueType(ValueType.PROCESS_DEFINITION_SUSPENSION)
                  .withDistributionPartitionId(p)
                  .exists())
          .describedAs("DISTRIBUTING to partition %d", p)
          .isTrue();
      assertThat(
              RecordingExporter.commandDistributionRecords(CommandDistributionIntent.ACKNOWLEDGED)
                  .withDistributionValueType(ValueType.PROCESS_DEFINITION_SUSPENSION)
                  .withDistributionPartitionId(p)
                  .exists())
          .describedAs("ACKNOWLEDGED by partition %d", p)
          .isTrue();
    }
  }

  @Test
  public void shouldRejectCreationOnAllPartitionsAfterDistribution() {
    // given
    final long key = deploy(Strings.newRandomValidBpmnId(), false, "task");
    final var suspended = engine.processDefinitionSuspension().withKey(key).suspend();
    awaitSuspendedOnAllPartitions(suspended.getKey());

    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      // when
      engine
          .processInstance()
          .ofBpmnProcessId("")
          .withProcessDefinitionKey(key)
          .onPartition(partition)
          .expectRejection()
          .create();

      // then
      final var rejection =
          RecordingExporter.processInstanceCreationRecords()
              .onlyCommandRejections()
              .withPartitionId(partition)
              .getFirst();
      assertThat(rejection)
          .hasRejectionType(RejectionType.INVALID_STATE)
          .hasRejectionReason(SUSPENDED_MESSAGE.formatted(key));
    }
  }

  @Test
  public void shouldAllowCreationBeforeDistributionArrives() {
    // given a SUSPEND command that does not reach partition 2 at first
    final long key = deploy(Strings.newRandomValidBpmnId(), false, "task");
    final var dropped = new AtomicBoolean();
    engine.interceptInterPartitionCommands(
        (receiver, valueType, intent, recordKey, command) ->
            receiver != 2
                || valueType != ValueType.PROCESS_DEFINITION_SUSPENSION
                || !dropped.compareAndSet(false, true));
    final var suspended = engine.processDefinitionSuspension().withKey(key).suspend();

    // when creating on partition 2 before the command arrives
    final long instanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("")
            .withProcessDefinitionKey(key)
            .onPartition(2)
            .create();

    // then it succeeds
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withIntent(ProcessInstanceCreationIntent.CREATED)
                .withPartitionId(2)
                .filter(r -> r.getValue().getProcessInstanceKey() == instanceKey)
                .exists())
        .isTrue();

    // when the command is redistributed and applied
    RecordingExporter.await()
        .untilAsserted(
            () -> {
              engine
                  .getClock()
                  .addTime(EngineConfiguration.DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL);
              assertThat(
                      RecordingExporter.processDefinitionSuspensionRecords(
                              ProcessDefinitionSuspensionIntent.SUSPENDED)
                          .withPartitionId(2)
                          .filter(r -> r.getKey() == suspended.getKey())
                          .exists())
                  .isTrue();
            });

    // then further creation on partition 2 is rejected
    engine
        .processInstance()
        .ofBpmnProcessId("")
        .withProcessDefinitionKey(key)
        .onPartition(2)
        .expectRejection()
        .create();
    final var rejection =
        RecordingExporter.processInstanceCreationRecords()
            .onlyCommandRejections()
            .withPartitionId(2)
            .getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(SUSPENDED_MESSAGE.formatted(key));
  }

  @Test
  public void shouldResumeOnAllPartitions() {
    // given
    final long key = deploy(Strings.newRandomValidBpmnId(), false, "task");
    final var suspended = engine.processDefinitionSuspension().withKey(key).suspend();
    awaitSuspendedOnAllPartitions(suspended.getKey());

    // when
    final var resumed = engine.processDefinitionSuspension().withKey(key).resume();

    // then
    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      final int p = partition;
      assertThat(
              RecordingExporter.processDefinitionSuspensionRecords(
                      ProcessDefinitionSuspensionIntent.RESUMED)
                  .withPartitionId(p)
                  .filter(r -> r.getKey() == resumed.getKey())
                  .exists())
          .describedAs("RESUMED on partition %d", p)
          .isTrue();
    }
    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      engine
          .processInstance()
          .ofBpmnProcessId("")
          .withProcessDefinitionKey(key)
          .onPartition(partition)
          .create();
    }
  }

  @Test
  public void shouldMoveStartEventSubscriptionsOnAllPartitions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final long v1 = deploy(processId, true, "task-v1");
    final long v2 = deploy(processId, true, "task-v2");

    // when
    final var suspended = engine.processDefinitionSuspension().withKey(v2).suspend();
    awaitSuspendedOnAllPartitions(suspended.getKey());

    // then every partition closed v2's subscription and reopened v1's
    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      final int p = partition;
      assertThat(
              RecordingExporter.messageStartEventSubscriptionRecords(
                      MessageStartEventSubscriptionIntent.DELETED)
                  .withPartitionId(p)
                  .withProcessDefinitionKey(v2)
                  .exists())
          .describedAs("v2 subscription deleted on partition %d", p)
          .isTrue();
      assertThat(
              RecordingExporter.messageStartEventSubscriptionRecords(
                      MessageStartEventSubscriptionIntent.CREATED)
                  .withPartitionId(p)
                  .withProcessDefinitionKey(v1)
                  .limit(2)
                  .count())
          .describedAs("v1 subscription reopened on partition %d", p)
          .isEqualTo(2);
    }

    // when
    final var resumed = engine.processDefinitionSuspension().withKey(v2).resume();

    // then every partition reopened v2's subscription
    for (int partition = 1; partition <= PARTITION_COUNT; partition++) {
      final int p = partition;
      assertThat(
              RecordingExporter.messageStartEventSubscriptionRecords(
                      MessageStartEventSubscriptionIntent.CREATED)
                  .withPartitionId(p)
                  .withProcessDefinitionKey(v2)
                  .limit(2)
                  .count())
          .describedAs(
              "v2 subscription reopened on partition %d after resume %d", p, resumed.getKey())
          .isEqualTo(2);
    }
  }

  /**
   * Reports how long the suspension takes to be applied on the other partitions. Only logs: the
   * test engine runs all partitions in one process, so the numbers do not represent a real cluster.
   */
  @Test
  public void shouldReportDistributionDelay() {
    // given
    final long key = deploy(Strings.newRandomValidBpmnId(), false, "task");
    final int rounds = 20;

    // when
    final long[] delays =
        IntStream.range(0, rounds)
            .mapToLong(
                round -> {
                  final var suspended = engine.processDefinitionSuspension().withKey(key).suspend();
                  final long origin = suspended.getTimestamp();
                  long applied = origin;
                  for (int partition = 2; partition <= PARTITION_COUNT; partition++) {
                    final int p = partition;
                    applied =
                        Math.max(
                            applied,
                            RecordingExporter.processDefinitionSuspensionRecords(
                                    ProcessDefinitionSuspensionIntent.SUSPENDED)
                                .withPartitionId(p)
                                .filter(r -> r.getKey() == suspended.getKey())
                                .getFirst()
                                .getTimestamp());
                  }
                  final var resumed = engine.processDefinitionSuspension().withKey(key).resume();
                  for (int partition = 2; partition <= PARTITION_COUNT; partition++) {
                    final int p = partition;
                    RecordingExporter.processDefinitionSuspensionRecords(
                            ProcessDefinitionSuspensionIntent.RESUMED)
                        .withPartitionId(p)
                        .filter(r -> r.getKey() == resumed.getKey())
                        .getFirst();
                  }
                  return applied - origin;
                })
            .toArray();

    // then
    final var stats = java.util.Arrays.stream(delays).summaryStatistics();
    LOG.info(
        "Suspension distribution delay over {} rounds (ms, EngineRule timing, not representative):"
            + " min={}, avg={}, max={}",
        rounds,
        stats.getMin(),
        stats.getAverage(),
        stats.getMax());
    assertThat(delays).hasSize(rounds);
  }
}
