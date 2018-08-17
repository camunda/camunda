/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowResponseObject;
import io.zeebe.gateway.util.RecordingStreamObserver;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public class DeployWorkflowEndpointTest {

  private final DeployWorkflowRequest request = DeployWorkflowRequest.getDefaultInstance();
  private final RecordingStreamObserver<DeployWorkflowResponse> streamObserver =
      new RecordingStreamObserver<>();
  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();
  @Mock private ResponseMapper responseMapper;
  @Mock private ClusterClient clusterClient;
  private EndpointManager endpointManager;
  private DeployWorkflowResponse response;

  @Before
  public void setUp() {
    initMocks(this);

    endpointManager = new EndpointManager(responseMapper, clusterClient, actorSchedulerRule.get());

    this.response =
        DeployWorkflowResponse.newBuilder()
            .addWorkflows(
                WorkflowResponseObject.newBuilder()
                    .setWorkflowKey(0)
                    .setVersion(0)
                    .setBpmnProcessId("demoProcess")
                    .setResourceName("demoProcess.bpmn"))
            .build();

    when(responseMapper.toDeployWorkflowResponse(any())).thenReturn(response);
  }

  @Test
  public void deployWorkflowShouldCheckCorrectInvocation() {
    // given
    final ActorFuture<DeploymentEvent> responseFuture = CompletableActorFuture.completed(null);
    when(clusterClient.sendDeployWorkflowRequest(any())).thenReturn(responseFuture);

    // when
    sendRequest(this.request);

    // then
    streamObserver.assertValues(response);
  }

  @Test
  public void deployWorkflowShouldProduceException() {
    // given
    final RuntimeException exception = new RuntimeException("no workflow to deploy");
    when(clusterClient.sendDeployWorkflowRequest(any()))
        .thenReturn(CompletableActorFuture.completedExceptionally(exception));

    // when
    sendRequest(DeployWorkflowRequest.getDefaultInstance());

    // then
    streamObserver.assertErrors(exception);
  }

  private void sendRequest(final DeployWorkflowRequest request) {
    endpointManager.deployWorkflow(request, this.streamObserver);
    actorSchedulerRule.workUntilDone();
  }
}
