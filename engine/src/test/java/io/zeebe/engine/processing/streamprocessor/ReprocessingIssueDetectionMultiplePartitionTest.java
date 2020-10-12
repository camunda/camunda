/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processing.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.RecordToWrite;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ReprocessingIssueDetectionMultiplePartitionTest {

  private static final int PARTITION_COUNT = 2;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final DeploymentDistributorMock deploymentDistributorMock =
      new DeploymentDistributorMock();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withDeploymentDistributor(deploymentDistributorMock);

  private long workflowInstanceKey;
  private Record<JobRecordValue> jobCreated;

  @Before
  public void setup() {
    // avoid that a deployment DISTRIBUTED event is written
    deploymentDistributorMock.pushDeploymentCallback = CompletableActorFuture::new;

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .done())
        .expectCreated()
        .deploy();

    workflowInstanceKey = engine.workflowInstance().ofBpmnProcessId("process").create();
    jobCreated = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() > jobCreated.getPosition())
                .withValueType(ValueType.DEPLOYMENT))
        .extracting(Record::getIntent)
        .doesNotContain(DeploymentIntent.DISTRIBUTED);

    engine.stop();
  }

  @Test
  public void shouldIgnoreDeploymentDistribution() {
    // given
    // force that a deployment DISTRIBUTED event is written on reprocessing which was not written on
    // the log stream before
    deploymentDistributorMock.pushDeploymentCallback = () -> CompletableActorFuture.completed(null);

    engine.writeRecords(
        RecordToWrite.command()
            .job(JobIntent.COMPLETE, jobCreated.getValue())
            .key(jobCreated.getKey()));

    // when
    engine.start();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .filterRootScope()
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    final var streamProcessor = engine.getStreamProcessor(1);
    assertThat(streamProcessor.isFailed()).isFalse();
    assertThat(streamProcessor.getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  private static final class DeploymentDistributorMock implements DeploymentDistributor {

    private final Map<Long, PendingDeploymentDistribution> pendingDeployments = new HashMap<>();
    private Supplier<ActorFuture<Void>> pushDeploymentCallback = CompletableActorFuture::new;

    @Override
    public ActorFuture<Void> pushDeployment(
        final long key, final long position, final DirectBuffer buffer) {
      pendingDeployments.put(
          key, new PendingDeploymentDistribution(buffer, position, PARTITION_COUNT));

      return pushDeploymentCallback.get();
    }

    @Override
    public PendingDeploymentDistribution removePendingDeployment(final long key) {
      return pendingDeployments.remove(key);
    }
  }
}
