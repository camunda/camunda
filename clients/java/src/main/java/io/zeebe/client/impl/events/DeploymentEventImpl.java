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
package io.zeebe.client.impl.events;

import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.impl.command.WorkflowImpl;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowResponseObject;
import java.util.ArrayList;
import java.util.List;

public class DeploymentEventImpl implements DeploymentEvent {

  private final List<Workflow> deployedWorkflows = new ArrayList<>();

  public DeploymentEventImpl(final DeployWorkflowResponse response) {
    for (final WorkflowResponseObject workflow : response.getWorkflowsList()) {
      deployedWorkflows.add(
          new WorkflowImpl(
              workflow.getBpmnProcessId(),
              workflow.getVersion(),
              workflow.getWorkflowKey(),
              workflow.getResourceName()));
    }
  }

  @Override
  public List<Workflow> getDeployedWorkflows() {
    return deployedWorkflows;
  }
}
