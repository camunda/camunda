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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class MultiPartitionDeploymentLifecycleTest {

  private static final int PARTITION_COUNT = 3;

  private static final String DMN_RESOURCE = "/dmn/decision-table.dmn";

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTestLifecycle() {
    // given - reprocess
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldReDistributeAfterRecovery")
            .startEvent()
            .endEvent()
            .done();

    // when
    engine.deployment().withXmlResource("process.bpmn", modelInstance).deploy();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(r -> r.getIntent().equals(DeploymentIntent.FULLY_DISTRIBUTED)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deployment was distributing to and
                // where it was completed. Since only the DeploymentDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records. It should be noted that the
                // DeploymentDistribution records are also written on partition 1!
                r.getValue() instanceof DeploymentDistributionRecordValue
                    ? ((DeploymentDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .startsWith(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND, 1),
            tuple(ProcessIntent.CREATED, RecordType.EVENT, 1),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(DeploymentDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(DeploymentDistributionIntent.COMPLETE, RecordType.COMMAND, 2),
            tuple(DeploymentDistributionIntent.COMPLETED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(DeploymentDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(DeploymentDistributionIntent.COMPLETE, RecordType.COMMAND, 3),
            tuple(DeploymentDistributionIntent.COMPLETED, RecordType.EVENT, 3))
        .endsWith(tuple(DeploymentIntent.FULLY_DISTRIBUTED, RecordType.EVENT, 1));

    assertThat(
            RecordingExporter.records()
                .withPartitionId(2)
                .limit(r -> r.getIntent().equals(DeploymentIntent.DISTRIBUTED))
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.DISTRIBUTE, DeploymentIntent.DISTRIBUTED);

    assertThat(
            RecordingExporter.records()
                .withPartitionId(3)
                .limit(r -> r.getIntent().equals(DeploymentIntent.DISTRIBUTED))
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.DISTRIBUTE, DeploymentIntent.DISTRIBUTED);
  }

  @Test
  public void shouldDistributeDmnResources() {
    // when
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();

    // then
    assertThat(RecordingExporter.deploymentRecords().withPartitionId(DEPLOYMENT_PARTITION).limit(3))
        .extracting(Record::getIntent)
        .hasSize(3)
        .contains(DeploymentIntent.FULLY_DISTRIBUTED);

    assertThat(RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED).limit(2))
        .extracting(Record::getPartitionId)
        .contains(2, 3);

    final var distributedEvent =
        RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED).getFirst().getValue();
    assertThat(distributedEvent.getDecisionRequirementsMetadata())
        .describedAs("Expect that decision requirements are distributed")
        .isNotEmpty();
    assertThat(distributedEvent.getDecisionsMetadata())
        .describedAs("Expect that decisions are distributed")
        .isNotEmpty();
  }

  @Test
  public void shouldRejectCompleteDeploymentDistributionWhenAlreadyCompleted() {
    // given
    engine.pauseProcessing(2);
    engine.pauseProcessing(3);

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("shouldReDistributeAfterRecovery")
                .startEvent()
                .endEvent()
                .done())
        .expectCreated()
        .deploy();

    RecordingExporter.records()
        .withPartitionId(2)
        .withValueType(ValueType.DEPLOYMENT)
        .withIntent(DeploymentIntent.DISTRIBUTE)
        .await();

    // first one is skipped
    engine.getClock().addTime(DeploymentRedistributor.DEPLOYMENT_REDISTRIBUTION_INTERVAL);
    Awaitility.await()
        .untilAsserted(
            () -> {
              // continue to add time to the clock until the deployment is re-distributed
              engine.getClock().addTime(DeploymentRedistributor.DEPLOYMENT_REDISTRIBUTION_INTERVAL);
              // todo: could benefit from RecordingExporter without
              assertThat(
                      RecordingExporter.records()
                          .withPartitionId(2)
                          .withValueType(ValueType.DEPLOYMENT)
                          .withIntent(DeploymentIntent.DISTRIBUTE)
                          .limit(2))
                  .hasSize(2);
            });

    // when
    engine.resumeProcessing(2);

    // then
    assertThat(
            RecordingExporter.deploymentDistributionRecords()
                .withIntent(DeploymentDistributionIntent.COMPLETE)
                .withPartitionId(2)
                .limit(3))
        .extracting(Record::getRecordType)
        .describedAs("Expect second command to be rejected")
        .containsExactlyInAnyOrder(
            RecordType.COMMAND, RecordType.COMMAND, RecordType.COMMAND_REJECTION);
  }
}
