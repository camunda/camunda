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
import io.zeebe.client.api.commands.UpdateRetriesJobCommandStep1;
import io.zeebe.client.api.commands.UpdateRetriesJobCommandStep1.UpdateRetriesJobCommandStep2;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;

public class JobUpdateRetriesCommandImpl
    implements UpdateRetriesJobCommandStep1, UpdateRetriesJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;

  public JobUpdateRetriesCommandImpl(GatewayStub asyncStub, long jobKey) {
    this.asyncStub = asyncStub;
    builder = UpdateJobRetriesRequest.newBuilder();
    builder.setJobKey(jobKey);
  }

  @Override
  public UpdateRetriesJobCommandStep2 retries(int retries) {
    builder.setRetries(retries);
    return this;
  }

  @Override
  public ZeebeFuture<Void> send() {
    final UpdateJobRetriesRequest request = builder.build();

    final ZeebeClientFutureImpl<Void, UpdateJobRetriesResponse> future =
        new ZeebeClientFutureImpl<>();

    asyncStub.updateJobRetries(request, future);
    return future;
  }
}
