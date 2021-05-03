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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiPartitionDeploymentRecoveryTest {

  public static final String PROCESS_ID = "process";
  public static final int PARTITION_ID = DEPLOYMENT_PARTITION;
  public static final int PARTITION_COUNT = 3;

  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldReDistributeAfterRecovery() {
    // given - reprocess
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldReDistributeAfterRecovery")
            .startEvent()
            .endEvent()
            .done();

    final var deployment =
        ENGINE.deployment().withXmlResource("process.bpmn", modelInstance).expectCreated().deploy();
    ENGINE.stop();

    // when
    RecordingExporter.reset();
    ENGINE.start();

    // then
    final var list =
        RecordingExporter.deploymentRecords()
            .withRecordKey(deployment.getKey())
            .withIntent(DeploymentIntent.FULLY_DISTRIBUTED)
            .collect(Collectors.toList());
    assertThat(list).hasSize(1);

    final var fullyDistributedDeployment = list.get(0);
    assertThat(fullyDistributedDeployment.getKey()).isNotNegative();
    assertThat(fullyDistributedDeployment.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(fullyDistributedDeployment.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(fullyDistributedDeployment.getIntent())
        .isEqualTo(DeploymentIntent.FULLY_DISTRIBUTED);

    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getIntent().equals(DeploymentIntent.FULLY_DISTRIBUTED))
                .withIntent(DeploymentDistributionIntent.DISTRIBUTING)
                .count())
        .isEqualTo(PARTITION_COUNT - 1);

    assertThat(
            RecordingExporter.deploymentRecords()
                .withIntent(DeploymentIntent.DISTRIBUTE)
                .limit(record -> record.getIntent().equals(DeploymentIntent.FULLY_DISTRIBUTED))
                .count())
        .isGreaterThanOrEqualTo(PARTITION_COUNT - 1);

    assertThat(
            RecordingExporter.records()
                .limit(record -> record.getIntent().equals(DeploymentIntent.FULLY_DISTRIBUTED))
                .withIntent(DeploymentDistributionIntent.COMPLETE)
                .count())
        .isGreaterThanOrEqualTo(PARTITION_COUNT - 1);
  }
}
