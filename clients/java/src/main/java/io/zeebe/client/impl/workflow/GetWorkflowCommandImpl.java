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
package io.zeebe.client.impl.workflow;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.commands.WorkflowResourceRequestStep1;
import io.zeebe.client.api.commands.WorkflowResourceRequestStep1.WorkflowResourceRequestStep2;
import io.zeebe.client.api.commands.WorkflowResourceRequestStep1.WorkflowResourceRequestStep3;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.events.WorkflowResourceImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowResponse;

public class GetWorkflowCommandImpl
    implements WorkflowResourceRequestStep1,
        WorkflowResourceRequestStep2,
        WorkflowResourceRequestStep3 {

  private final GatewayStub gatewayStub;
  private final Builder request;

  public GetWorkflowCommandImpl(GatewayStub gatewayStub) {
    this.gatewayStub = gatewayStub;
    this.request = GetWorkflowRequest.newBuilder();
  }

  @Override
  public WorkflowResourceRequestStep2 bpmnProcessId(String bpmnProcessId) {
    request.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  @Override
  public WorkflowResourceRequestStep3 workflowKey(long workflowKey) {
    request.setWorkflowKey(workflowKey);
    return this;
  }

  @Override
  public WorkflowResourceRequestStep3 version(int version) {
    request.setVersion(version);
    return this;
  }

  @Override
  public WorkflowResourceRequestStep3 latestVersion() {
    request.setVersion(LATEST_VERSION);
    return this;
  }

  @Override
  public ZeebeFuture<WorkflowResource> send() {
    final ZeebeClientFutureImpl<WorkflowResource, GetWorkflowResponse> future =
        new ZeebeClientFutureImpl<>(WorkflowResourceImpl::new);

    gatewayStub.getWorkflow(request.build(), future);

    return future;
  }
}
