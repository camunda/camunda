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
}
