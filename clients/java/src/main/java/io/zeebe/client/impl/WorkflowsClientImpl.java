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
package io.zeebe.client.impl;

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.commands.CancelWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1;
import io.zeebe.client.api.commands.PublishMessageCommandStep1;
import io.zeebe.client.api.commands.UpdatePayloadWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.WorkflowRequestStep1;
import io.zeebe.client.api.commands.WorkflowResourceRequestStep1;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.impl.workflow.DeployWorkflowCommandImpl;
import io.zeebe.client.impl.workflow.PublishMessageCommandImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;

public class WorkflowsClientImpl implements WorkflowClient {

  private final GatewayStub asyncStub;
  private final ZeebeClientConfiguration config;

  public WorkflowsClientImpl(final GatewayStub asyncStub, ZeebeClientConfiguration config) {
    this.asyncStub = asyncStub;
    this.config = config;
  }

  @Override
  public DeployWorkflowCommandStep1 newDeployCommand() {
    return new DeployWorkflowCommandImpl(asyncStub);
  }

  @Override
  public CreateWorkflowInstanceCommandStep1 newCreateInstanceCommand() {
    return null;
  }

  @Override
  public CancelWorkflowInstanceCommandStep1 newCancelInstanceCommand(
      final WorkflowInstanceEvent event) {
    return null;
  }

  @Override
  public UpdatePayloadWorkflowInstanceCommandStep1 newUpdatePayloadCommand(
      final WorkflowInstanceEvent event) {
    return null;
  }

  @Override
  public PublishMessageCommandStep1 newPublishMessageCommand() {
    return new PublishMessageCommandImpl(asyncStub, config);
  }

  @Override
  public WorkflowResourceRequestStep1 newResourceRequest() {
    return null;
  }

  @Override
  public WorkflowRequestStep1 newWorkflowRequest() {
    return null;
  }
}
