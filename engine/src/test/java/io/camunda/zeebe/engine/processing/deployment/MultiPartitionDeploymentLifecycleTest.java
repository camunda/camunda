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

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
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
    final var deploymentPartitionRecords =
        RecordingExporter.records().withPartitionId(1).limit(10).collect(Collectors.toList());

    assertThat(deploymentPartitionRecords).hasSize(10);

    assertThat(deploymentPartitionRecords.subList(0, 5))
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND),
            tuple(ProcessIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentDistributionIntent.DISTRIBUTING, RecordType.EVENT),
            tuple(DeploymentDistributionIntent.DISTRIBUTING, RecordType.EVENT));

    assertThat(deploymentPartitionRecords.subList(5, 9))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r -> ((DeploymentDistributionRecordValue) r.getValue()).getPartitionId())
        .containsSubsequence(
            tuple(DeploymentDistributionIntent.COMPLETE, RecordType.COMMAND, 2),
            tuple(DeploymentDistributionIntent.COMPLETED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(DeploymentDistributionIntent.COMPLETE, RecordType.COMMAND, 3),
            tuple(DeploymentDistributionIntent.COMPLETED, RecordType.EVENT, 3));

    assertThat(deploymentPartitionRecords.subList(9, deploymentPartitionRecords.size()))
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(tuple(DeploymentIntent.FULLY_DISTRIBUTED, RecordType.EVENT));

    assertThat(RecordingExporter.records().withPartitionId(2).limit(2).collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.DISTRIBUTE, DeploymentIntent.DISTRIBUTED);

    assertThat(RecordingExporter.records().withPartitionId(3).limit(2).collect(Collectors.toList()))
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
}
