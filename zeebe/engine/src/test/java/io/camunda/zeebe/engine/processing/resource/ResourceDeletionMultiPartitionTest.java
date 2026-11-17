/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ResourceDeletionMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;
  private static final String DMN_RESOURCE = "/dmn/decision-table.dmn";
  private static final String FORM_RESOURCE = "/form/test-form-1.form";
  private static final String RPA_RESOURCE = "/resource/test-rpa-1.rpa";
  private static final String JOB_TYPE = "task";
  private static final int INSTANCE_PARTITION = 2;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTestDmnLifecycle() {
    // given
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();

    final var resourceKey =
        RecordingExporter.decisionRequirementsRecords()
            .withIntent(DecisionRequirementsIntent.CREATED)
            .withDecisionRequirementsId("force_users")
            .getFirst()
            .getKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(DecisionIntent.DELETED, RecordType.EVENT, 1),
            tuple(DecisionRequirementsIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .toList())
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              DecisionIntent.DELETED,
              DecisionRequirementsIntent.DELETED,
              ResourceDeletionIntent.DELETED);
    }
  }

  @Test
  public void shouldTestBpmnLifecycle() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final long resourceKey =
        engine
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(ProcessIntent.DRAINING, RecordType.EVENT, 1),
            tuple(ProcessIntent.DELETING, RecordType.EVENT, 1),
            tuple(ProcessIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .toList())
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              ProcessIntent.DRAINING,
              ProcessIntent.DELETING,
              ProcessIntent.DELETED,
              ProcessIntent.DELETE_COMPLETE,
              ResourceDeletionIntent.DELETED);
    }
  }

  @Test
  public void shouldFullyDeleteAcrossAllPartitionsWhenDeletingProcessWithoutInstances() {
    // given - a process with no running instances on any partition
    final var processId = Strings.newRandomValidBpmnId();
    final long processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then - every partition drains (immediately, since there are no instances) and reports; the
    // deployment partition clears each report and marks the definition fully deleted exactly once
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.DELETE_COMPLETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withPartitionId(1)
                .limit(PARTITION_COUNT)
                .count())
        .describedAs("each partition's drain report is cleared on the deployment partition")
        .isEqualTo(PARTITION_COUNT);
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.FULLY_DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withPartitionId(1)
                .limit(1)
                .count())
        .describedAs("the definition is reported fully deleted exactly once")
        .isEqualTo(1);
  }

  @Test
  public void shouldNotBlockDeploymentsWhenDeletingProcessWithActiveInstanceOnOtherPartition() {
    // given - a process with an active instance stopped at a user task on partition 2 (non-source)
    final var processId = Strings.newRandomValidBpmnId();
    final long processDefinitionKey =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    engine.processInstance().ofBpmnProcessId(processId).onPartition(2).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withPartitionId(2)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // when - the definition is deleted; distribution to partition 2 gets stuck on the active
    // instance (delete still succeeds on the source partition and returns)
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // and - a deployment is issued afterwards (deploy() awaits full distribution to all partitions)
    final var secondProcessId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(secondProcessId).startEvent().endEvent().done())
        .deploy();

    // then - the later deployment still reaches the affected partition 2 (queue not blocked)
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.CREATED)
                .withPartitionId(2)
                .withBpmnProcessId(secondProcessId)
                .exists())
        .describedAs("a deployment after the deletion should still reach partition 2")
        .isTrue();

    // and - the resource-deletion distribution itself completes
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .withDistributionValueType(ValueType.RESOURCE_DELETION)
                .withIntent(CommandDistributionIntent.FINISHED)
                .exists())
        .describedAs("RESOURCE_DELETION distribution should finish, not block the DEPLOYMENT queue")
        .isTrue();
  }

  @Test
  public void shouldNotFullyDeleteWhileInstanceStillActiveOnAnotherPartition() {
    // given - a process whose only active instance lives on a non-deployment partition
    final var processId = Strings.newRandomValidBpmnId();
    final long processDefinitionKey = deployServiceTaskProcess(processId);
    final long instanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .onPartition(INSTANCE_PARTITION)
            .create();
    awaitJobCreated(instanceKey);

    // when - the definition is deleted; every partition drains, but partition INSTANCE_PARTITION
    // keeps draining because its instance is still active
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then - the two instance-free partitions report drained and the deployment partition clears
    // them (PARTITION_COUNT - 1 reports)
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETE_COMPLETED)
        .withProcessDefinitionKey(processDefinitionKey)
        .withPartitionId(1)
        .limit(PARTITION_COUNT - 1)
        .await();

    // and - with one partition still draining its active instance, the definition must NOT be
    // reported fully deleted (FULLY_DELETED requires every seeded partition to have reported)
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processRecords()
                        .withIntent(ProcessIntent.FULLY_DELETED)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .withPartitionId(1)
                        .exists()))
        .describedAs("FULLY_DELETED must not be emitted while a partition is still draining")
        .isFalse();
  }

  @Test
  public void shouldRejectRetroactiveHistoryDeletionWhileDrainingOnAnotherPartition() {
    // given - a process draining with its only active instance on a non-deployment partition, and
    // the deployment partition already finalized locally (it has no instance of its own)
    final var processId = Strings.newRandomValidBpmnId();
    final long processDefinitionKey = deployServiceTaskProcess(processId);
    final long instanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .onPartition(INSTANCE_PARTITION)
            .create();
    awaitJobCreated(instanceKey);
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    // wait until only the instance's partition is still draining: the deployment partition has
    // finalized locally (definition gone from its primary storage) but the cluster drain is not
    // done
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETE_COMPLETED)
        .withProcessDefinitionKey(processDefinitionKey)
        .withPartitionId(1)
        .limit(PARTITION_COUNT - 1)
        .await();

    // when - a history deletion is requested for the locally-gone definition. The service resolves
    // the type from secondary storage, so the command carries PROCESS_DEFINITION as in production.
    final var rejection =
        engine.resourceDeletion().withResourceKey(processDefinitionKey).expectRejection().delete();

    // then - rejected as already-being-deleted: the definition is still draining cluster-wide, so
    // the caller is told to wait rather than getting a misleading not-found
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);

    // and - no history-deletion batch operation was spawned
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.batchOperationCreationRecords()
                        .withIntent(BatchOperationIntent.CREATE)
                        .exists()))
        .describedAs("no batch operation while still draining on another partition")
        .isFalse();

    // and - the still-active instance on the other partition was not terminated
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processInstanceRecords(
                            ProcessInstanceIntent.ELEMENT_TERMINATED)
                        .withProcessInstanceKey(instanceKey)
                        .withElementType(BpmnElementType.PROCESS)
                        .exists()))
        .describedAs("an active instance on another partition must not be terminated")
        .isFalse();
  }

  @Test
  public void shouldFullyDeleteAfterActiveInstanceCompletesOnAnotherPartition() {
    // given - a process draining with an active instance on a non-deployment partition
    final var processId = Strings.newRandomValidBpmnId();
    final long processDefinitionKey = deployServiceTaskProcess(processId);
    final long instanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .onPartition(INSTANCE_PARTITION)
            .create();
    awaitJobCreated(instanceKey);
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    // wait until the affected partition has actually entered DRAINING before completing the
    // instance
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(processDefinitionKey)
        .withPartitionId(INSTANCE_PARTITION)
        .await();

    // and - the deployment partition emitted a DRAINING event
    final var drainingOnDeploymentPartition =
        RecordingExporter.processRecords()
            .withIntent(ProcessIntent.DRAINING)
            .withProcessDefinitionKey(processDefinitionKey)
            .withPartitionId(1)
            .getFirst();
    assertThat(((ProcessRecord) drainingOnDeploymentPartition.getValue()).getDrainPartitions())
        .describedAs("the deployment partition's DRAINING event records every partition to drain")
        .containsExactlyInAnyOrder(
            IntStream.rangeClosed(1, PARTITION_COUNT).boxed().toArray(Integer[]::new));

    // when - the last active instance completes on its partition, freeing that partition to drain.
    // The job client routes the command to the job key's partition, so it lands on
    // INSTANCE_PARTITION.
    engine.job().ofInstance(instanceKey).withType(JOB_TYPE).complete();

    // then - all partitions' drain reports are cleared on the deployment partition and the
    // definition is reported fully deleted cluster-wide exactly once
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.DELETE_COMPLETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withPartitionId(1)
                .limit(PARTITION_COUNT)
                .count())
        .describedAs("every partition's drain report is cleared on the deployment partition")
        .isEqualTo(PARTITION_COUNT);
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.FULLY_DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withPartitionId(1)
                .limit(1)
                .count())
        .describedAs("the definition is reported fully deleted exactly once")
        .isEqualTo(1);
  }

  @Test
  public void shouldTestFormLifecycle() {
    // given
    final var resourceKey =
        engine
            .deployment()
            .withXmlClasspathResource(FORM_RESOURCE)
            .deploy()
            .getValue()
            .getFormMetadata()
            .getFirst()
            .getFormKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(FormIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .toList())
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              FormIntent.DELETED,
              ResourceDeletionIntent.DELETED);
    }
  }

  @Test
  public void shouldTestResourceLifecycle() {
    // given
    final var resourceKey =
        engine
            .deployment()
            .withJsonClasspathResource(RPA_RESOURCE)
            .deploy()
            .getValue()
            .getResourceMetadata()
            .getFirst()
            .getResourceKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(ResourceDeletionIntent.DELETE, RecordType.COMMAND, 1),
            tuple(ResourceIntent.DELETED, RecordType.EVENT, 1),
            tuple(ResourceDeletionIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(ResourceDeletionIntent.DELETED))
                  .toList())
          .extracting(Record::getIntent)
          .endsWith(
              ResourceDeletionIntent.DELETE,
              ResourceDeletionIntent.DELETING,
              ResourceIntent.DELETED,
              ResourceDeletionIntent.DELETED);
    }
  }

  private long deployServiceTaskProcess(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private void awaitJobCreated(final long processInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .await();
  }
}
