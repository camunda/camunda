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
import io.zeebe.client.api.commands.UpdatePayloadWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.UpdatePayloadWorkflowInstanceCommandStep1.UpdatePayloadWorkflowInstanceCommandStep2;
import io.zeebe.client.impl.CommandWithPayload;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadResponse;

public class UpdateWorkflowInstancePayloadCommandImpl
    extends CommandWithPayload<UpdatePayloadWorkflowInstanceCommandStep2>
    implements UpdatePayloadWorkflowInstanceCommandStep1,
        UpdatePayloadWorkflowInstanceCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;

  public UpdateWorkflowInstancePayloadCommandImpl(
      GatewayStub asyncStub, ZeebeObjectMapper objectMapper, long elementInstanceKey) {
    super(objectMapper);
    this.asyncStub = asyncStub;
    this.builder = UpdateWorkflowInstancePayloadRequest.newBuilder();
    builder.setElementInstanceKey(elementInstanceKey);
  }

  @Override
  public ZeebeFuture<Void> send() {
    final UpdateWorkflowInstancePayloadRequest request = builder.build();

    final ZeebeClientFutureImpl<Void, UpdateWorkflowInstancePayloadResponse> future =
        new ZeebeClientFutureImpl<>();

    asyncStub.updateWorkflowInstancePayload(request, future);
    return future;
  }

  @Override
  protected UpdatePayloadWorkflowInstanceCommandStep2 setPayloadInternal(String payload) {
    builder.setPayload(payload);
    return this;
  }
}
