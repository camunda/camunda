/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
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
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
  private static final BpmnModelInstance PROCESS_2 =
      Bpmn.createExecutableProcess("process2").startEvent().endEvent().done();
  private static final String DMN_DECISION_TABLE = "/dmn/decision-table.dmn";
  private static final String DMN_DECISION_TABLE_V2 = "/dmn/decision-table_v2.dmn";

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

    Assertions.assertThat(resource).hasResource(bpmnXml(PROCESS));

    final List<ProcessMetadataValue> deployedProcesses =
        createdDeployment.getValue().getProcessesMetadata();

    assertThat(deployedProcesses).hasSize(1);
    Assertions.assertThat(deployedProcesses.get(0))
        .hasBpmnProcessId("shouldCreateDeploymentOnAllPartitions")
        .hasVersion(1)
        .hasProcessDefinitionKey(getDeployedProcess(deployment, 0).getProcessDefinitionKey())
        .hasResourceName("process.bpmn");
  }

  @Test
  public void shouldOnlyDistributeFromDeploymentPartition() {
    // when
    final long deploymentKey1 = ENGINE.deployment().withXmlResource(PROCESS).deploy().getKey();

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
    final long deploymentKey = ENGINE.deployment().withXmlResource(PROCESS).deploy().getKey();

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
  public void shouldCreateDeploymentResourceWithMultipleProcesses() {
    // given

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", PROCESS)
            .withXmlResource("process2.bpmn", PROCESS_2)
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
        .flatExtracting(DeploymentRecordValue::getProcessesMetadata)
        .extracting(ProcessMetadataValue::getBpmnProcessId)
        .containsOnly("process", "process2");
  }

  @Test
  public void shouldIncrementProcessVersions() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldIncrementProcessVersions")
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

    var deployedProcesses = firstCreatedDeployment.getValue().getProcessesMetadata();
    assertThat(deployedProcesses).flatExtracting(ProcessMetadataValue::getVersion).containsOnly(1);

    final Record<DeploymentRecordValue> secondCreatedDeployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(secondDeployment.getKey())
            .getFirst();

    deployedProcesses = secondCreatedDeployments.getValue().getProcessesMetadata();
    assertThat(deployedProcesses).flatExtracting(ProcessMetadataValue::getVersion).containsOnly(2);
  }

  @Test
  public void shouldFilterDuplicateProcess() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", PROCESS).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", PROCESS).deploy();

    // then
    assertThat(repeated.getKey()).isGreaterThan(original.getKey());

    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = repeated.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isOne();

    assertThat(
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final var repeatedWfs =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getProcessesMetadata().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedWfs.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedWfs.forEach(repeatedWf -> assertSameProcess(originalProcesses.get(0), repeatedWf));
  }

  @Test
  public void shouldNotFilterDifferentProcesses() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlResource("process.bpmn", PROCESS).deploy();

    // when
    final BpmnModelInstance sameBpmnIdModel =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlResource("process.bpmn", sameBpmnIdModel).deploy();

    // then
    final var originalProcesses = original.getValue().getProcessesMetadata();
    final var repeatedProcesses = repeated.getValue().getProcessesMetadata();
    assertThat(repeatedProcesses.size()).isEqualTo(originalProcesses.size()).isOne();

    assertDifferentProcesses(originalProcesses.get(0), repeatedProcesses.get(0));

    assertThat(
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final List<ProcessMetadataValue> repeatedWfs =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getProcessesMetadata().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedWfs.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedWfs.forEach(
        repeatedWf -> assertDifferentProcesses(originalProcesses.get(0), repeatedWf));
  }

  @Test
  public void shouldFilterDuplicateDmnResource() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    assertThat(repeated.getKey()).isGreaterThan(original.getKey());

    final var originalDecision = original.getValue().getDecisionsMetadata();
    final var originalDrg = original.getValue().getDecisionRequirementsMetadata();
    final var repeatedDecision = repeated.getValue().getDecisionsMetadata();
    final var repeatedDrg = repeated.getValue().getDecisionRequirementsMetadata();
    assertThat(repeatedDecision.size()).isEqualTo(originalDecision.size()).isOne();
    assertThat(repeatedDrg.size()).isEqualTo(originalDrg.size()).isOne();

    assertThat(
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final var repeatedDecisions =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionsMetadata().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedDecisions.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedDecisions.forEach(r -> assertSameDecision(originalDecision.get(0), r));

    final var repeatedDrgs =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionRequirementsMetadata().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedDrgs.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedDrgs.forEach(r -> assertSameDrg(originalDrg.get(0), r));
  }

  @Test
  public void shouldNotFilterDifferentDmnResource() {
    // given
    final Record<DeploymentRecordValue> original =
        ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // when
    final Record<DeploymentRecordValue> repeated =
        ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE_V2).deploy();

    // then
    assertThat(repeated.getKey()).isGreaterThan(original.getKey());

    final var originalDecision = original.getValue().getDecisionsMetadata();
    final var originalDrg = original.getValue().getDecisionRequirementsMetadata();
    final var repeatedDecision = repeated.getValue().getDecisionsMetadata();
    final var repeatedDrg = repeated.getValue().getDecisionRequirementsMetadata();
    assertThat(repeatedDecision.size()).isEqualTo(originalDecision.size()).isOne();
    assertThat(repeatedDrg.size()).isEqualTo(originalDrg.size()).isOne();

    assertThat(
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final var repeatedDecisions =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionsMetadata().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedDecisions.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedDecisions.forEach(r -> assertDifferentDecision(originalDecision.get(0), r));

    final var repeatedDrgs =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionRequirementsMetadata().get(0))
            .collect(Collectors.toList());

    assertThat(repeatedDrgs.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedDrgs.forEach(r -> assertDifferentDrg(originalDrg.get(0), r));
  }

  private void assertSameProcess(
      final ProcessMetadataValue original, final ProcessMetadataValue repeated) {
    Assertions.assertThat(repeated)
        .hasVersion(original.getVersion())
        .hasProcessDefinitionKey(original.getProcessDefinitionKey())
        .hasResourceName(original.getResourceName())
        .hasBpmnProcessId(original.getBpmnProcessId());
  }

  private void assertDifferentProcesses(
      final ProcessMetadataValue original, final ProcessMetadataValue repeated) {
    assertThat(original.getProcessDefinitionKey()).isLessThan(repeated.getProcessDefinitionKey());
    assertThat(original.getVersion()).isLessThan(repeated.getVersion());
  }

  private void assertSameDecision(
      final DecisionRecordValue original, final DecisionRecordValue repeated) {
    Assertions.assertThat(repeated)
        .hasDecisionId(original.getDecisionId())
        .hasDecisionName(original.getDecisionName())
        .hasVersion(original.getVersion())
        .hasDecisionKey(original.getDecisionKey())
        .hasDecisionRequirementsId(original.getDecisionRequirementsId())
        .hasDecisionRequirementsKey(original.getDecisionRequirementsKey());
  }

  private void assertDifferentDecision(
      final DecisionRecordValue original, final DecisionRecordValue repeated) {
    assertThat(original.getVersion()).isLessThan(repeated.getVersion());
    assertThat(original.getDecisionKey()).isLessThan(repeated.getDecisionKey());
    assertThat(original.getDecisionRequirementsKey())
        .isLessThan(repeated.getDecisionRequirementsKey());
  }

  private void assertSameDrg(
      final DecisionRequirementsMetadataValue original,
      final DecisionRequirementsMetadataValue repeated) {
    Assertions.assertThat(repeated)
        .hasDecisionRequirementsId(original.getDecisionRequirementsId())
        .hasDecisionRequirementsName(original.getDecisionRequirementsName())
        .hasDecisionRequirementsVersion(original.getDecisionRequirementsVersion())
        .hasDecisionRequirementsKey(original.getDecisionRequirementsKey())
        .hasNamespace(original.getNamespace())
        .hasResourceName(original.getResourceName())
        .hasChecksum(original.getChecksum());
  }

  private void assertDifferentDrg(
      final DecisionRequirementsMetadataValue original,
      final DecisionRequirementsMetadataValue repeated) {
    assertThat(original.getDecisionRequirementsVersion())
        .isLessThan(repeated.getDecisionRequirementsVersion());
    assertThat(original.getDecisionRequirementsKey())
        .isLessThan(repeated.getDecisionRequirementsKey());
  }

  private byte[] bpmnXml(final BpmnModelInstance definition) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, definition);
    return outStream.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private ProcessMetadataValue getDeployedProcess(
      final Record<DeploymentRecordValue> record, final int offset) {
    return record.getValue().getProcessesMetadata().get(offset);
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
