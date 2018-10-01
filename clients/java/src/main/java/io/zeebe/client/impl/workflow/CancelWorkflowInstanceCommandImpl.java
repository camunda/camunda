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
import io.zeebe.client.api.commands.CancelWorkflowInstanceCommandStep1;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceResponse;

public class CancelWorkflowInstanceCommandImpl implements CancelWorkflowInstanceCommandStep1 {

  private final GatewayStub asyncStub;
  private final Builder builder;

  public CancelWorkflowInstanceCommandImpl(GatewayStub asyncStub, long workflowInstanceKey) {
    this.asyncStub = asyncStub;
    this.builder = CancelWorkflowInstanceRequest.newBuilder();
    builder.setWorkflowInstanceKey(workflowInstanceKey);
  }

  @Override
  public ZeebeFuture<Void> send() {
    final CancelWorkflowInstanceRequest request = builder.build();

    final ZeebeClientFutureImpl<Void, CancelWorkflowInstanceResponse> future =
        new ZeebeClientFutureImpl<>();

    asyncStub.cancelWorkflowInstance(request, future);
    return future;
  }
}
