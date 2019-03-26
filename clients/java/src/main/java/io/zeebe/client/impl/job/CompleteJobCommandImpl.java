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
package io.zeebe.client.impl.job;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.CompleteJobCommandStep1;
import io.zeebe.client.impl.CommandWithVariables;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest.Builder;

public class CompleteJobCommandImpl extends CommandWithVariables<CompleteJobCommandStep1>
    implements CompleteJobCommandStep1 {

  private final GatewayStub asyncStub;
  private final Builder builder;

  public CompleteJobCommandImpl(GatewayStub asyncStub, ZeebeObjectMapper objectMapper, long key) {
    super(objectMapper);
    this.asyncStub = asyncStub;
    builder = CompleteJobRequest.newBuilder();
    builder.setJobKey(key);
  }

  @Override
  public ZeebeFuture<Void> send() {
    final CompleteJobRequest request = builder.build();

    final ZeebeClientFutureImpl<Void, GatewayOuterClass.CompleteJobResponse> future =
        new ZeebeClientFutureImpl<>();

    asyncStub.completeJob(request, future);
    return future;
  }

  @Override
  protected CompleteJobCommandStep1 setVariablesInternal(String variables) {
    builder.setVariables(variables);
    return this;
  }
}
