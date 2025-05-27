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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor;
import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.engine.state.immutable.DeploymentState.PendingDeploymentVisitor;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
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
public class DeploymentRedistributorTest {

  @Mock private DeploymentDistributionCommandSender deploymentDistributionCommandSender;
  @Mock private DeploymentState deploymentState;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private StreamProcessorContext context;

  @Mock private RoutingState routingState;
  private DeploymentRedistributor deploymentRedistributor;
  private ArgumentCaptor<Runnable> taskCaptor;
  private long recordKey;
  private DirectBuffer resourceBuffer;
  private DeploymentRecord deploymentRecord;

  @BeforeEach
  public void setUp() {
    when(context.getPartitionId()).thenReturn(1);

    when(routingState.currentPartitions()).thenReturn(Set.of(1, 2));
    when(routingState.desiredPartitions()).thenReturn(Set.of(1, 2, 3));

    final RoutingInfo routingInfo =
        RoutingInfo.dynamic(routingState, new StaticRoutingInfo(Set.of(1, 2), 2));

    deploymentRedistributor =
        new DeploymentRedistributor(
            deploymentDistributionCommandSender, deploymentState, routingInfo);

    recordKey = Protocol.encodePartitionId(1, 100);
    final DeploymentResource deploymentResource =
        new DeploymentResource()
            .setResourceName("test.bpmn")
            .setResource(BufferUtil.wrapString("test"));
    resourceBuffer = BufferUtil.createCopy(deploymentResource);
    deploymentRecord = new DeploymentRecord();
    deploymentRecord.wrap(resourceBuffer);
    taskCaptor = forClass(Runnable.class);
  }

  @Test
  void shouldNotRedistributeToScalingPartitions() {
    // given

    doAnswer(
            invocation -> {
              final PendingDeploymentVisitor visitor = invocation.getArgument(0);
              visitor.visit(recordKey, 1, resourceBuffer);
              // Revisit this to simulate interval
              visitor.visit(recordKey, 1, resourceBuffer);
              visitor.visit(recordKey, 2, resourceBuffer);
              // Revisit this to simulate interval
              visitor.visit(recordKey, 2, resourceBuffer);
              // Simulate multiple visits for the deployment on the scaling partition
              visitor.visit(recordKey, 3, resourceBuffer);
              visitor.visit(recordKey, 3, resourceBuffer);
              visitor.visit(recordKey, 3, resourceBuffer);
              visitor.visit(recordKey, 3, resourceBuffer);
              return null;
            })
        .when(deploymentState)
        .foreachPendingDeploymentDistribution(any());

    // when
    deploymentRedistributor.onRecovered(context);
    Awaitility.await()
        .untilAsserted(
            () -> verify(context.getScheduleService()).runAtFixedRate(any(), taskCaptor.capture()));
    final Runnable scheduledTask = taskCaptor.getValue();
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
    doAnswer(
            invocation -> {
              final PendingDeploymentVisitor visitor = invocation.getArgument(0);
              visitor.visit(recordKey, 1, resourceBuffer);
              // Revisit this to simulate interval
              visitor.visit(recordKey, 1, resourceBuffer);
              visitor.visit(recordKey, 2, resourceBuffer);
              // Revisit this to simulate interval
              visitor.visit(recordKey, 2, resourceBuffer);
              // Simulate multiple visits for the deployment on the scaling partition
              visitor.visit(recordKey, 3, resourceBuffer);
              visitor.visit(recordKey, 3, resourceBuffer);
              visitor.visit(recordKey, 3, resourceBuffer);
              // Simulate scaling up is finished
              when(routingState.currentPartitions()).thenReturn(Set.of(1, 2, 3));
              visitor.visit(recordKey, 3, resourceBuffer);
              visitor.visit(recordKey, 3, resourceBuffer);
              return null;
            })
        .when(deploymentState)
        .foreachPendingDeploymentDistribution(any());

    // when
    deploymentRedistributor.onRecovered(context);
    Awaitility.await()
        .untilAsserted(
            () -> verify(context.getScheduleService()).runAtFixedRate(any(), taskCaptor.capture()));
    final Runnable scheduledTask = taskCaptor.getValue();
    scheduledTask.run();
    // then
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 1, deploymentRecord);
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 2, deploymentRecord);
    verify(deploymentDistributionCommandSender, times(1))
        .distributeToPartition(recordKey, 3, deploymentRecord);
  }
}
