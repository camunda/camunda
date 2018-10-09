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
import io.zeebe.client.api.commands.WorkflowRequestStep1;
import io.zeebe.client.api.commands.Workflows;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.events.WorkflowsImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsResponse;

public class ListWorkflowsCommandImpl implements WorkflowRequestStep1 {

  private final GatewayStub gatewayStub;
  private final Builder request;

  public ListWorkflowsCommandImpl(GatewayStub gatewayStub) {
    this.gatewayStub = gatewayStub;
    this.request = ListWorkflowsRequest.newBuilder();
  }

  @Override
  public WorkflowRequestStep1 bpmnProcessId(String bpmnProcessId) {
    request.setBpmnProcessId(bpmnProcessId);
    return this;
  }

  @Override
  public ZeebeFuture<Workflows> send() {
    final ZeebeClientFutureImpl<Workflows, ListWorkflowsResponse> future =
        new ZeebeClientFutureImpl<>(WorkflowsImpl::new);

    gatewayStub.listWorkflows(request.build(), future);

    return future;
  }
}
