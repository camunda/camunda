/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CreateDeploymentMultiplePartitionsTest {

  public static final String PROCESS_ID = "process";
  public static final int PARTITION_ID = DEPLOYMENT_PARTITION;
  public static final int PARTITION_COUNT = 3;
  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
  private static final BpmnModelInstance WORKFLOW_2 =
      Bpmn.createExecutableProcess("process2").startEvent().endEvent().done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateDeploymentOnAllPartitions() {
    // when
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldCreateDeploymentOnAllPartitions")
            .startEvent()
            .endEvent()
            .done();
    final BpmnModelInstance secondNoopModel =
        Bpmn.createExecutableProcess("shouldCreateDeploymentOnAllPartitionsSecondNoopDeployment")
            .startEvent()
            .endEvent()
            .done();
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource("process.bpmn", modelInstance).deploy();
    final Record<DeploymentRecordValue> secondDeployment =
        ENGINE.deployment().withXmlResource("secondNoopModel.bpmn", secondNoopModel).deploy();

    // then
    assertThat(deployment.getKey()).isNotNegative();

    assertThat(deployment.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(deployment.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    final var deploymentRecords =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == DeploymentIntent.FULLY_DISTRIBUTED
                        && r.getKey() == secondDeployment.getKey())
            .withRecordKey(deployment.getKey())
            .collect(Collectors.toList());

    final var listOfFullyDistributed =
        deploymentRecords.stream()
            .filter(r -> r.getIntent() == DeploymentIntent.FULLY_DISTRIBUTED)
            .collect(Collectors.toList());
    assertThat(listOfFullyDistributed).hasSize(1);

    final var fullyDistributedDeployment = listOfFullyDistributed.get(0);
    assertThat(fullyDistributedDeployment.getKey()).isNotNegative();
    assertThat(fullyDistributedDeployment.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(fullyDistributedDeployment.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(fullyDistributedDeployment.getIntent())
        .isEqualTo(DeploymentIntent.FULLY_DISTRIBUTED);

    assertThat(
            deploymentRecords.stream()
                .filter(r -> r.getIntent() == DeploymentIntent.DISTRIBUTE)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    assertThat(
            deploymentRecords.stream()
                .filter(r -> r.getIntent() == DeploymentDistributionIntent.DISTRIBUTING)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    assertThat(
            deploymentRecords.stream()
                .filter(r -> r.getIntent() == DeploymentDistributionIntent.COMPLETE)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    //    todo(zell): https://github.com/zeebe-io/zeebe/issues/6314 fully distributed contains
    //    currently no longer any resources
    //
    //    assertDeploymentEventResources(
    //        DEPLOYMENT_PARTITION,
    //        DeploymentIntent.CREATED,
    //        deployment.getKey(),
    //        (createdDeployment) ->
    //            assertDeploymentRecord(fullyDistributedDeployment, createdDeployment));

    ENGINE
        .getPartitionIds()
        .forEach(
            partitionId -> {
              if (DEPLOYMENT_PARTITION == partitionId) {
                return;
              }

              assertDeploymentEventResources(
                  partitionId,
                  DeploymentIntent.DISTRIBUTED,
                  deployment.getKey(),
                  (createdDeployment) -> assertDeploymentRecord(deployment, createdDeployment));
            });
  }

  private void assertDeploymentRecord(
      final Record<DeploymentRecordValue> deployment,
      final Record<DeploymentRecordValue> createdDeployment) {
    final DeploymentResource resource = createdDeployment.getValue().getResources().get(0);

    Assertions.assertThat(resource).hasResource(bpmnXml(WORKFLOW));

    final List<DeployedWorkflow> deployedWorkflows =
        createdDeployment.getValue().getDeployedWorkflows();

    assertThat(deployedWorkflows).hasSize(1);
    Assertions.assertThat(deployedWorkflows.get(0))
        .hasBpmnProcessId("shouldCreateDeploymentOnAllPartitions")
        .hasVersion(1)
        .hasWorkflowKey(getDeployedWorkflow(deployment, 0).getWorkflowKey())
        .hasResourceName("process.bpmn");
  }

  @Test
  public void shouldOnlyDistributeFromDeploymentPartition() {
    // when
    final long deploymentKey1 = ENGINE.deployment().withXmlResource(WORKFLOW).deploy().getKey();

    // then
    final List<Record<DeploymentDistributionRecordValue>> deploymentRecords =
        RecordingExporter.deploymentDistributionRecords()
            .withRecordKey(deploymentKey1)
            .withIntent(DeploymentDistributionIntent.DISTRIBUTING)
            .limit(PARTITION_COUNT - 1)
            .asList();

    assertThat(deploymentRecords).hasSize(PARTITION_COUNT - 1);
    assertThat(deploymentRecords)
        .extracting(Record::getValue)
        .extracting(DeploymentDistributionRecordValue::getPartitionId)
        .doesNotContain(DEPLOYMENT_PARTITION);
  }

  @Test
  public void shouldWriteDistributingRecordsForOtherPartitions() {
    // when
    final long deploymentKey = ENGINE.deployment().withXmlResource(WORKFLOW).deploy().getKey();

    // then
    final List<Record<DeploymentDistributionRecordValue>> deploymentDistributionRecords =
        RecordingExporter.deploymentDistributionRecords()
            .withIntent(DeploymentDistributionIntent.DISTRIBUTING)
            .limit(2)
            .asList();

    assertThat(deploymentDistributionRecords)
        .extracting(Record::getKey)
        .containsOnly(deploymentKey);

    assertThat(deploymentDistributionRecords)
        .extracting(Record::getPartitionId)
        .containsOnly(DEPLOYMENT_PARTITION);

    assertThat(deploymentDistributionRecords)
        .extracting(Record::getValue)
        .extracting(DeploymentDistributionRecordValue::getPartitionId)
        .containsExactly(2, 3);
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleWorkflows() {
    // given

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", WORKFLOW)
            .withXmlResource("process2.bpmn", WORKFLOW_2)
            .deploy();

    // then
    assertThat(deployment.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    final List<Record<DeploymentRecordValue>> distributedDeployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(deployment.getKey())
            .limit(PARTITION_COUNT - 1)
            .asList();

    assertThat(distributedDeployments)
        .hasSize(PARTITION_COUNT - 1)
        .extracting(Record::getValue)
        .flatExtracting(DeploymentRecordValue::getDeployedWorkflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly("process", "process2");
  }

  @Test
  public void shouldIncrementWorkflowVersions() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldIncrementWorkflowVersions")
            .startEvent()
            .endEvent()
            .done();
    final Record<DeploymentRecordValue> firstDeployment =
        ENGINE.deployment().withXmlResource("process1.bpmn", modelInstance).deploy();

    // when
    final Record<DeploymentRecordValue> secondDeployment =
        ENGINE.deployment().withXmlResource("process2.bpmn", modelInstance).deploy();

    // then
    final Record<DeploymentRecordValue> firstCreatedDeployment =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(firstDeployment.getKey())
            .getFirst();

    var deployedWorkflows = firstCreatedDeployment.getValue().getDeployedWorkflows();
    assertThat(deployedWorkflows).flatExtracting(DeployedWorkflow::getVersion).containsOnly(1);

    final Record<DeploymentRecordValue> secondCreatedDeployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(secondDeployment.getKey())
            .getFirst();

    deployedWorkflows = secondCreatedDeployments.getValue().getDeployedWorkflows();
    assertThat(deployedWorkflows).flatExtracting(DeployedWorkflow::getVersion).containsOnly(2);
  }

  @Test
  public void shouldFilterDuplicateWorkflow() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", WORKFLOW).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", WORKFLOW).deploy();

    // then
    assertThat(repeated.getKey()).isGreaterThan(original.getKey());

    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertThat(
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final List<DeployedWorkflow> repeatedWfs =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDeployedWorkflows().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedWfs.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedWfs.forEach(repeatedWf -> assertSameResource(originalWorkflows.get(0), repeatedWf));
  }

  @Test
  public void shouldNotFilterDifferentWorkflows() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", WORKFLOW).deploy();

    // when
    final BpmnModelInstance sameBpmnIdModel =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", sameBpmnIdModel).deploy();

    // then
    final List<DeployedWorkflow> originalWorkflows = original.getValue().getDeployedWorkflows();
    final List<DeployedWorkflow> repeatedWorkflows = repeated.getValue().getDeployedWorkflows();
    assertThat(repeatedWorkflows.size()).isEqualTo(originalWorkflows.size()).isOne();

    assertDifferentResources(originalWorkflows.get(0), repeatedWorkflows.get(0));

    assertThat(
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final List<DeployedWorkflow> repeatedWfs =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDeployedWorkflows().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedWfs.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedWfs.forEach(
        repeatedWf -> assertDifferentResources(originalWorkflows.get(0), repeatedWf));
  }

  private void assertSameResource(
      final DeployedWorkflow original, final DeployedWorkflow repeated) {
    Assertions.assertThat(repeated)
        .hasVersion(original.getVersion())
        .hasWorkflowKey(original.getWorkflowKey())
        .hasResourceName(original.getResourceName())
        .hasBpmnProcessId(original.getBpmnProcessId());
  }

  private void assertDifferentResources(
      final DeployedWorkflow original, final DeployedWorkflow repeated) {
    assertThat(original.getWorkflowKey()).isLessThan(repeated.getWorkflowKey());
    assertThat(original.getVersion()).isLessThan(repeated.getVersion());
  }

  private byte[] bpmnXml(final BpmnModelInstance definition) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, definition);
    return outStream.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private DeployedWorkflow getDeployedWorkflow(
      final Record<DeploymentRecordValue> record, final int offset) {
    return record.getValue().getDeployedWorkflows().get(offset);
  }

  private void assertDeploymentEventResources(
      final int expectedPartition,
      final DeploymentIntent deploymentIntent,
      final long expectedKey,
      final Consumer<Record<DeploymentRecordValue>> deploymentAssert) {
    final Record deploymentCreatedEvent =
        RecordingExporter.deploymentRecords()
            .withPartitionId(expectedPartition)
            .withIntent(deploymentIntent)
            .withRecordKey(expectedKey)
            .getFirst();

    assertThat(deploymentCreatedEvent.getKey()).isEqualTo(expectedKey);
    assertThat(deploymentCreatedEvent.getPartitionId()).isEqualTo(expectedPartition);

    deploymentAssert.accept(deploymentCreatedEvent);
  }
}
