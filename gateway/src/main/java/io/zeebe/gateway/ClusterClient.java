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

import io.zeebe.gateway.api.commands.DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.List;

public class ClusterClient {

  private final ZeebeClient client;

  public ClusterClient(final ZeebeClient client) {
    this.client = client;
  }

  public ActorFuture<Topology> sendHealthRequest(final HealthRequest healthRequest) {
    return client.newTopologyRequest().send();
  }

  public ActorFuture<DeploymentEvent> sendDeployWorkflowRequest(
      final DeployWorkflowRequest deployRequest) {

    if (deployRequest.getWorkflowsList().size() == 0) {
      throw new RuntimeException("no workflow to deploy");
    }

    final List<WorkflowRequestObject> workflowsList = deployRequest.getWorkflowsList();
    WorkflowRequestObject cursor = workflowsList.get(0);
    DeployWorkflowCommandBuilderStep2 clusterRequestStep2 =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceBytes(cursor.getDefinition().toByteArray(), cursor.getName());

    for (int i = 1; i < workflowsList.size(); i++) {
      cursor = workflowsList.get(i);
      clusterRequestStep2 =
          clusterRequestStep2.addResourceBytes(
              cursor.getDefinition().toByteArray(), cursor.getName());
    }

    return clusterRequestStep2.send();
  }
}
