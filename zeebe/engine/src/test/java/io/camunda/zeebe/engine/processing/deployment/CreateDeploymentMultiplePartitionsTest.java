/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;
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
  private static final String DMN_DECISION_TABLE_RENAMED =
      "/dmn/decision-table-with-renamed-drg-and-decision.dmn";

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
                    r.getIntent() == CommandDistributionIntent.FINISHED
                        && r.getKey() == secondDeployment.getKey())
            .withRecordKey(deployment.getKey())
            .toList();

    final var listOfFinishedDistributions =
        deploymentRecords.stream()
            .filter(r -> r.getIntent() == CommandDistributionIntent.FINISHED)
            .toList();
    assertThat(listOfFinishedDistributions).hasSize(1);

    final var fullyDistributedDeployment = listOfFinishedDistributions.get(0);
    assertThat(fullyDistributedDeployment.getKey()).isNotNegative();
    assertThat(fullyDistributedDeployment.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(fullyDistributedDeployment.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(fullyDistributedDeployment.getIntent())
        .isEqualTo(CommandDistributionIntent.FINISHED);

    assertThat(
            deploymentRecords.stream()
                .filter(r -> r.getIntent() == DeploymentIntent.CREATE)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    assertThat(
            deploymentRecords.stream()
                .filter(r -> r.getIntent() == CommandDistributionIntent.DISTRIBUTING)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    assertThat(
            deploymentRecords.stream()
                .filter(r -> r.getIntent() == CommandDistributionIntent.ACKNOWLEDGE)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    ENGINE
        .getPartitionIds()
        .forEach(
            partitionId -> {
              assertDeploymentEventResources(
                  partitionId,
                  DeploymentIntent.CREATED,
                  deployment.getKey(),
                  (createdDeployment) ->
                      assertDeploymentRecordWithoutResources(deployment, createdDeployment));
            });
  }

  @Test
  public void shouldOnlyDistributeFromDeploymentPartition() {
    // when
    final long deploymentKey1 = ENGINE.deployment().withXmlResource(PROCESS).deploy().getKey();

    // then
    final var distributionRecords =
        RecordingExporter.commandDistributionRecords()
            .withRecordKey(deploymentKey1)
            .withIntent(CommandDistributionIntent.DISTRIBUTING)
            .limit(PARTITION_COUNT - 1)
            .asList();

    assertThat(distributionRecords).hasSize(PARTITION_COUNT - 1);
    assertThat(distributionRecords)
        .extracting(Record::getValue)
        .extracting(CommandDistributionRecordValue::getPartitionId)
        .doesNotContain(DEPLOYMENT_PARTITION);
  }

  @Test
  public void shouldWriteDistributingRecordsForOtherPartitions() {
    // when
    final long deploymentKey = ENGINE.deployment().withXmlResource(PROCESS).deploy().getKey();

    // then
    final var commandDistributionRecords =
        RecordingExporter.commandDistributionRecords()
            .withIntent(CommandDistributionIntent.DISTRIBUTING)
            .limit(2)
            .asList();

    assertThat(commandDistributionRecords).extracting(Record::getKey).containsOnly(deploymentKey);

    assertThat(commandDistributionRecords)
        .extracting(Record::getPartitionId)
        .containsOnly(DEPLOYMENT_PARTITION);

    assertThat(commandDistributionRecords)
        .extracting(Record::getValue)
        .extracting(CommandDistributionRecordValue::getPartitionId)
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

    final var deployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(deployment.getKey())
            .limit(PARTITION_COUNT)
            .asList();

    assertThat(deployments)
        .hasSize(PARTITION_COUNT)
        .extracting(Record::getValue)
        .flatExtracting(DeploymentRecordValue::getProcessesMetadata)
        .extracting(ProcessMetadataValue::getBpmnProcessId)
        .containsOnly("process", "process2");
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleProcessWithSameResourceName() {
    // given

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", PROCESS)
            .withXmlResource("process.bpmn", PROCESS_2)
            .deploy();

    // then
    assertThat(deployment.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    final var deployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(deployment.getKey())
            .limit(PARTITION_COUNT)
            .asList();

    assertThat(deployments)
        .hasSize(PARTITION_COUNT)
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
            .withPartitionId(DEPLOYMENT_PARTITION)
            .withRecordKey(firstDeployment.getKey())
            .getFirst();

    var deployedProcesses = firstCreatedDeployment.getValue().getProcessesMetadata();
    assertThat(deployedProcesses).flatExtracting(ProcessMetadataValue::getVersion).containsOnly(1);

    final Record<DeploymentRecordValue> secondCreatedDeployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withPartitionId(DEPLOYMENT_PARTITION)
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
            RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final var repeatedWfs =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getProcessesMetadata().get(0))
            .toList();

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
            RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final List<ProcessMetadataValue> repeatedWfs =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getProcessesMetadata().get(0))
            .toList();

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
            RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final var repeatedDecisions =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionsMetadata().get(0))
            .toList();

    assertThat(repeatedDecisions.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedDecisions.forEach(r -> assertSameDecision(originalDecision.get(0), r));

    final var repeatedDrgs =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionRequirementsMetadata().get(0))
            .toList();

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
            RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
                .withRecordKey(repeated.getKey())
                .limit(PARTITION_COUNT - 1)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    final var repeatedDecisions =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionsMetadata().get(0))
            .toList();

    assertThat(repeatedDecisions.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedDecisions.forEach(r -> assertDifferentDecision(originalDecision.get(0), r));

    final var repeatedDrgs =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
            .withRecordKey(repeated.getKey())
            .limit(PARTITION_COUNT - 1)
            .map(r -> r.getValue().getDecisionRequirementsMetadata().get(0))
            .toList();

    assertThat(repeatedDrgs.size()).isEqualTo(PARTITION_COUNT - 1);
    repeatedDrgs.forEach(r -> assertDifferentDrg(originalDrg.get(0), r));
  }

  @Test
  public void shouldWriteProcessCreatedEventsOnAllPartitions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                "process.bpmn",
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .deploy();

    // then
    ENGINE.forEachPartition(
        partitionId -> {
          final var record =
              RecordingExporter.processRecords()
                  .withPartitionId(partitionId)
                  .withIntents(ProcessIntent.CREATED)
                  .withBpmnProcessId(processId)
                  .limit(1)
                  .getFirst();
          assertThat(record).isNotNull();
          assertThat(record.getRecordVersion()).isEqualTo(2);
          assertThat(record.getValue().getResourceName()).isEqualTo("process.bpmn");
          assertThat(record.getValue().getVersion()).isEqualTo(1);
          assertThat(record.getValue().getDeploymentKey()).isEqualTo(deployment.getKey());
          assertThat(record.getKey()).isEqualTo(record.getValue().getProcessDefinitionKey());
        });
  }

  @Test
  public void shouldWriteProcessCreatedEventsWithSameKeys() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // then
    assertThat(
            RecordingExporter.processRecords()
                .withIntents(ProcessIntent.CREATED)
                .withBpmnProcessId(processId)
                .limit(3)
                .map(Record::getKey)
                .distinct())
        .describedAs("All created events get the same key")
        .hasSize(1);
  }

  @Test
  public void shouldWriteDrgAndDecisionCreatedEventsWithSameKeys() {
    // given
    final var drgId = "force_users";
    final var decisionId = "jedi_or_sith";

    // when
    ENGINE.deployment().withXmlClasspathResource(DMN_DECISION_TABLE).deploy();

    // then
    assertThat(
            RecordingExporter.decisionRequirementsRecords()
                .withIntents(DecisionRequirementsIntent.CREATED)
                .withDecisionRequirementsId(drgId)
                .limit(3)
                .map(Record::getKey)
                .distinct())
        .describedAs("All created events get the same key")
        .hasSize(1);
    assertThat(
            RecordingExporter.decisionRecords()
                .withIntents(DecisionIntent.CREATED)
                .withDecisionId(decisionId)
                .limit(3)
                .map(Record::getKey)
                .distinct())
        .describedAs("All created events get the same key")
        .hasSize(1);
  }

  @Test
  public void shouldCreateProcessForTenant() {
    // given
    final String tenant = "tenant";
    final String processId = Strings.newRandomValidBpmnId();

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                "process.xml",
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .withTenantId(tenant)
            .deploy();

    // then
    assertThat(deployment.getValue().getTenantId()).isEqualTo(tenant);
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.processRecords()
                  .withIntent(ProcessIntent.CREATED)
                  .withPartitionId(partitionId)
                  .limit(1))
          .extracting(Record::getValue)
          .extracting(
              ProcessMetadataValue::getBpmnProcessId,
              ProcessMetadataValue::getVersion,
              ProcessMetadataValue::getProcessDefinitionKey,
              TenantOwned::getTenantId)
          .describedAs("Processes are created for correct tenant")
          .containsExactly(
              tuple(
                  processId,
                  1,
                  deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
                  tenant));
    }
  }

  @Test
  public void shouldCreateDmnForTenant() {
    // given
    final String tenant = "tenant";
    final var drgId = "star-wars";
    final var decisionId = "sith_or_jedi";

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_DECISION_TABLE_RENAMED)
            .withTenantId(tenant)
            .deploy();

    // then
    assertThat(deployment.getValue().getTenantId()).isEqualTo(tenant);
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.decisionRequirementsRecords()
                  .withIntent(DecisionRequirementsIntent.CREATED)
                  .withPartitionId(partitionId)
                  .limit(1))
          .extracting(Record::getValue)
          .extracting(
              DecisionRequirementsMetadataValue::getDecisionRequirementsId,
              DecisionRequirementsMetadataValue::getDecisionRequirementsVersion,
              DecisionRequirementsMetadataValue::getDecisionRequirementsKey,
              TenantOwned::getTenantId)
          .describedAs("DRGs are created for correct tenant")
          .containsExactly(
              tuple(
                  drgId,
                  1,
                  deployment
                      .getValue()
                      .getDecisionRequirementsMetadata()
                      .get(0)
                      .getDecisionRequirementsKey(),
                  tenant));

      assertThat(
              RecordingExporter.decisionRecords()
                  .withIntent(DecisionIntent.CREATED)
                  .withPartitionId(partitionId)
                  .limit(1))
          .extracting(Record::getValue)
          .extracting(
              DecisionRecordValue::getDecisionId,
              DecisionRecordValue::getVersion,
              DecisionRecordValue::getDecisionKey,
              DecisionRecordValue::getDecisionRequirementsId,
              DecisionRecordValue::getDecisionRequirementsKey,
              TenantOwned::getTenantId)
          .describedAs("Decisions are created for correct tenant")
          .containsExactly(
              tuple(
                  decisionId,
                  1,
                  deployment.getValue().getDecisionsMetadata().get(0).getDecisionKey(),
                  drgId,
                  deployment
                      .getValue()
                      .getDecisionRequirementsMetadata()
                      .get(0)
                      .getDecisionRequirementsKey(),
                  tenant));
    }
  }

  private void assertDeploymentRecordWithoutResources(
      final Record<DeploymentRecordValue> deployment,
      final Record<DeploymentRecordValue> createdDeployment) {

    assertThat(createdDeployment.getValue().getResources()).isEmpty();

    final List<ProcessMetadataValue> deployedProcesses =
        createdDeployment.getValue().getProcessesMetadata();

    assertThat(deployedProcesses).hasSize(1);
    Assertions.assertThat(deployedProcesses.get(0))
        .hasBpmnProcessId("shouldCreateDeploymentOnAllPartitions")
        .hasVersion(1)
        .hasProcessDefinitionKey(getDeployedProcess(deployment, 0).getProcessDefinitionKey())
        .hasResourceName("process.bpmn");
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
