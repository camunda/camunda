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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiPartitionDeploymentLifecycleTest {

  public static final String PROCESS_ID = "process";
  public static final int PARTITION_ID = DEPLOYMENT_PARTITION;
  public static final int PARTITION_COUNT = 3;

  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);

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
    ENGINE.deployment().withXmlResource("process.bpmn", modelInstance).deploy();

    // then
    final var deploymentPartitionRecords =
        RecordingExporter.records().withPartitionId(1).limit(14).collect(Collectors.toList());

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
}
