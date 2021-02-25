/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processing.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class DeploymentReprocessingTest {

  private static final int PARTITION_COUNT = 2;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final DeploymentDistributorMock deploymentDistributorMock =
      spy(new DeploymentDistributorMock());

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withDeploymentDistributor(deploymentDistributorMock);

  @Before
  public void setup() {
    // complete the deployment distribution immediately to write the DISTRIBUTED event
    deploymentDistributorMock.pushDeploymentCallback = () -> CompletableActorFuture.completed(null);

    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().done())
        .expectCreated()
        .deploy();

    RecordingExporter.deploymentRecords(DeploymentIntent.FULLY_DISTRIBUTED).await();

    engine.stop();

    // the deployment is distributed once on processing
    verify(deploymentDistributorMock).pushDeployment(anyLong(), anyLong(), any());
    verify(deploymentDistributorMock).removePendingDeployment(anyLong());
  }

  @Test
  public void shouldNotDistributeDeploymentOnReprocessing() {
    // when
    engine.start();

    // then
    engine.awaitReprocessingCompleted();

    verifyNoMoreInteractions(deploymentDistributorMock);
  }

  private static class DeploymentDistributorMock implements DeploymentDistributor {

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
