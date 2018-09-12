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
package io.zeebe.gateway.factories;

import io.zeebe.gateway.api.commands.Workflow;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.impl.event.DeploymentEventImpl;
import io.zeebe.gateway.impl.event.WorkflowImpl;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowResponseObject;
import java.util.ArrayList;
import java.util.List;

public class DeploymentEventFactory implements TestFactory<DeploymentEvent> {

  private final List<Workflow> workflows = new ArrayList<>();

  @Override
  public DeploymentEvent getFixture() {
    final DeploymentEventImpl event = new DeploymentEventImpl(null);

    final WorkflowImpl workflow = new WorkflowImpl();
    workflow.setWorkflowKey(1);
    workflow.setVersion(1);
    workflow.setResourceName("demoProcess.bpmn");
    workflow.setBpmnProcessId("demoProcess");

    workflows.add(workflow);
    event.setWorkflows(workflows);
    return event;
  }

  public int size() {
    return workflows.size();
  }

  public boolean contains(final WorkflowResponseObject grpcWorkflow) {
    return workflows
        .stream()
        .anyMatch(
            workflow ->
                workflow.getVersion() == grpcWorkflow.getVersion()
                    && workflow.getWorkflowKey() == grpcWorkflow.getWorkflowKey()
                    && workflow.getBpmnProcessId().equals(grpcWorkflow.getBpmnProcessId())
                    && workflow.getResourceName().equals(grpcWorkflow.getResourceName()));
  }
}
