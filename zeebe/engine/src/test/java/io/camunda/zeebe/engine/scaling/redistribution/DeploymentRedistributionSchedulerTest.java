/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributionScheduler;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.engine.state.routing.RoutingInfo.StaticRoutingInfo;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.impl.StreamProcessorContext;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, ProcessingStateExtension.class})
public class DeploymentRedistributionSchedulerTest {

  @Mock private DeploymentDistributionCommandSender deploymentDistributionCommandSender;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableRoutingState routingState;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private StreamProcessorContext context;

  private DeploymentRedistributionScheduler deploymentRedistributor;
  private ArgumentCaptor<Runnable> taskCaptor;
  private long recordKey;
  private DeploymentRecord deploymentRecord;

  @BeforeEach
  public void setUp() {
    when(context.getPartitionId()).thenReturn(1);
    taskCaptor = forClass(Runnable.class);

    final var deploymentState = processingState.getDeploymentState();
    routingState = processingState.getRoutingState();
    routingState.initializeRoutingInfo(2);
    routingState.setDesiredPartitions(Set.of(1, 2, 3), 239123L);

    final RoutingInfo routingInfo =
        RoutingInfo.dynamic(routingState, new StaticRoutingInfo(Set.of(1, 2), 2));

    deploymentRedistributor =
        new DeploymentRedistributionScheduler(
            deploymentDistributionCommandSender, deploymentState, routingInfo);

    final DeploymentResource deploymentResource =
        new DeploymentResource()
            .setResourceName("test.bpmn")
            .setResource(BufferUtil.wrapString("test"));
    final DirectBuffer resourceBuffer = BufferUtil.createCopy(deploymentResource);

    recordKey = Protocol.encodePartitionId(1, 100);
    deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(resourceBuffer);

    deploymentState.storeDeploymentRecord(recordKey, deploymentRecord);

    // Add pending deployment distributions for partitions 1, 2, and 3
    routingState
        .desiredPartitions()
        .forEach(
            partition -> deploymentState.addPendingDeploymentDistribution(recordKey, partition));
  }

  @Test
  void shouldNotRedistributeToScalingPartitions() {
    // given
    // when
    deploymentRedistributor.onRecovered(context);

    // capture the scheduled task to run it immediately
    Awaitility.await()
        .untilAsserted(
            () -> verify(context.getScheduleService()).runAtFixedRate(any(), taskCaptor.capture()));
    final Runnable scheduledTask = taskCaptor.getValue();

    // run the scheduled task twice, since first one always does not try distributing the records
    scheduledTask.run();
    scheduledTask.run();

    // then
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 1, deploymentRecord);
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 2, deploymentRecord);
    verify(deploymentDistributionCommandSender, times(0))
        .distributeToPartition(recordKey, 3, deploymentRecord);
  }

  @Test
  void shouldRedistributeToScaledPartition() {
    // given
    // when
    deploymentRedistributor.onRecovered(context);

    // capture the scheduled task to run it immediately
    Awaitility.await()
        .untilAsserted(
            () -> verify(context.getScheduleService()).runAtFixedRate(any(), taskCaptor.capture()));
    final Runnable scheduledTask = taskCaptor.getValue();

    // run the scheduled task twice, since first one always does not try distributing the records
    scheduledTask.run();
    scheduledTask.run();

    // then
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 1, deploymentRecord);
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 2, deploymentRecord);
    verify(deploymentDistributionCommandSender, times(0))
        .distributeToPartition(recordKey, 3, deploymentRecord);

    // then
    // Partition 3 is now scaled up, so it should be redistributed
    routingState.activatePartition(3);

    // run the scheduled task twice, since first one always does not try distributing the records
    scheduledTask.run();
    scheduledTask.run();
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 3, deploymentRecord);
  }
}
