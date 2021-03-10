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
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.ProcessIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
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

    assertThat(deploymentPartitionRecords)
        .extracting(Record::getIntent, Record::getRecordType)
        .containsExactly(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND),
            tuple(ProcessIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT),
            tuple(DeploymentDistributionIntent.DISTRIBUTING, RecordType.EVENT),
            tuple(DeploymentDistributionIntent.DISTRIBUTING, RecordType.EVENT),
            tuple(DeploymentDistributionIntent.COMPLETE, RecordType.COMMAND),
            tuple(DeploymentDistributionIntent.COMPLETE, RecordType.COMMAND),
            tuple(DeploymentDistributionIntent.COMPLETED, RecordType.EVENT),
            tuple(DeploymentDistributionIntent.COMPLETED, RecordType.EVENT),
            tuple(DeploymentIntent.FULLY_DISTRIBUTED, RecordType.EVENT));

    assertThat(RecordingExporter.records().withPartitionId(2).limit(2).collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.DISTRIBUTE, DeploymentIntent.DISTRIBUTED);

    assertThat(RecordingExporter.records().withPartitionId(3).limit(2).collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.DISTRIBUTE, DeploymentIntent.DISTRIBUTED);
  }
}
