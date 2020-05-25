/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.broker;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.broker.backpressure.ResourceExhaustedException;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterStateImpl;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandResponseBuilder;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

public class BrokerClientBackPressureTest {

  private static final int CLIENT_MAX_REQUESTS = 128;
  @Rule public StubBrokerRule broker = new StubBrokerRule();
  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();
  @Rule public ExpectedException exception = ExpectedException.none();
  @Rule public TestName testContext = new TestName();
  private BrokerClient client;
  private ControlledActorClock clock;

  @Before
  public void setUp() {
    final GatewayCfg configuration = new GatewayCfg();
    configuration
        .getCluster()
        .setHost("0.0.0.0")
        .setPort(SocketUtil.getNextAddress().port())
        .setContactPoint(broker.getSocketAddress().toString())
        .setRequestTimeout("3s");
    configuration
        .getBackpressure()
        .setEnabled(true)
        .getAimdCfg()
        .setInitialLimit(1)
        .setMinLimit(1)
        .setMaxLimit(1)
        .setRequestTimeout("3s");
    clock = new ControlledActorClock();

    final AtomixCluster atomixCluster = mock(AtomixCluster.class);
    final ClusterMembershipService memberShipService = mock(ClusterMembershipService.class);
    when(atomixCluster.getMembershipService()).thenReturn(memberShipService);

    client = new BrokerClientImpl(configuration, atomixCluster, clock);

    ((BrokerClientImpl) client).getTransport().registerEndpoint(0, broker.getSocketAddress());

    final BrokerClusterStateImpl topology = new BrokerClusterStateImpl();
    topology.addPartitionIfAbsent(START_PARTITION_ID);
    topology.setPartitionLeader(START_PARTITION_ID, 0);

    ((BrokerTopologyManagerImpl) client.getTopologyManager()).setTopology(topology);
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void shouldReturnErrorOnBackpressure() {
    // given
    final BrokerCreateWorkflowInstanceRequest request = new BrokerCreateWorkflowInstanceRequest();
    request.setPartitionId(1);
    client.sendRequest(request);

    // when - then
    assertThatThrownBy(() -> client.sendRequest(request).join())
        .isInstanceOf(ExecutionException.class)
        .hasCause(new ResourceExhaustedException(1))
        .hasMessage(
            "Congestion detected for partition 1, load will be limited until there is no more congestion");
  }

  @Test
  public void shouldNotBlockRequestForDifferentPartitionWhenBackpressure() {
    // given
    broker.onExecuteCommandRequest(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE).doNotRespond();
    final BrokerActivateJobsRequest requestInflight = new BrokerActivateJobsRequest("job");
    requestInflight.setPartitionId(2);
    client.sendRequest(requestInflight);

    // when
    registerCreateWfCommand();
    final BrokerCreateWorkflowInstanceRequest requestShouldSucceed =
        new BrokerCreateWorkflowInstanceRequest();
    requestShouldSucceed.setPartitionId(1);
    final BrokerResponse<WorkflowInstanceCreationRecord> response =
        client.sendRequest(requestShouldSucceed).join();

    // then
    assertThat(response.isResponse()).isTrue();
  }

  public void registerCreateWfCommand() {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(
                ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE)
            .respondWith()
            .event()
            .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .key(r -> r.key())
            .value()
            .allOf((r) -> r.getCommand())
            .done();

    builder.register();
  }
}
