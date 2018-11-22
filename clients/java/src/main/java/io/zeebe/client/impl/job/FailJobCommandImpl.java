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
import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.api.commands.FailJobCommandStep1.FailJobCommandStep2;
import io.zeebe.client.api.commands.FinalCommandStep;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;

public class FailJobCommandImpl implements FailJobCommandStep1, FailJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;

  public FailJobCommandImpl(GatewayStub asyncStub, long key) {
    this.asyncStub = asyncStub;
    builder = FailJobRequest.newBuilder();
    builder.setJobKey(key);
  }

  @Override
  public FailJobCommandStep2 retries(int retries) {
    builder.setRetries(retries);
    return this;
  }

  @Override
  public FinalCommandStep errorMessage(String errorMsg) {
    builder.setErrorMessage(errorMsg);
    return this;
  }

  @Override
  public ZeebeFuture<Void> send() {
    final FailJobRequest request = builder.build();

    final ZeebeClientFutureImpl<Void, FailJobResponse> future = new ZeebeClientFutureImpl<>();

    asyncStub.failJob(request, future);
    return future;
  }
}
