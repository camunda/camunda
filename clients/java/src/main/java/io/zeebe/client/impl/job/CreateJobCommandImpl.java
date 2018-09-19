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
import io.zeebe.client.api.commands.CreateJobCommandStep1;
import io.zeebe.client.api.commands.CreateJobCommandStep1.CreateJobCommandStep2;
import io.zeebe.client.api.response.CreateJobResponse;
import io.zeebe.client.impl.ArgumentUtil;
import io.zeebe.client.impl.CommandWithPayload;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.response.CreateJobResponseImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobRequest.Builder;
import java.util.HashMap;
import java.util.Map;

public class CreateJobCommandImpl extends CommandWithPayload<CreateJobCommandStep2>
    implements CreateJobCommandStep1, CreateJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Map<String, Object> customHeaders;

  public CreateJobCommandImpl(GatewayStub asyncStub) {
    this.asyncStub = asyncStub;
    builder = CreateJobRequest.newBuilder();
    customHeaders = new HashMap<>();

    builder.setRetries(CreateJobCommandStep1.DEFAULT_RETRIES);
  }

  @Override
  public CreateJobCommandStep2 addCustomHeader(final String key, final Object value) {
    ArgumentUtil.ensureNotNull("key", key);
    customHeaders.put(key, value);
    return this;
  }

  @Override
  public CreateJobCommandStep2 addCustomHeaders(final Map<String, Object> headers) {
    customHeaders.putAll(headers);
    return this;
  }

  @Override
  public CreateJobCommandStep2 retries(final int retries) {
    ArgumentUtil.ensureGreaterThan("retries", retries, 0);
    builder.setRetries(retries);
    return this;
  }

  public CreateJobCommandStep2 jobType(final String type) {
    ArgumentUtil.ensureNotNullNorEmpty("jobType", type);
    builder.setJobType(type);
    return this;
  }

  @Override
  protected CreateJobCommandStep2 setPayloadInternal(String payload) {
    builder.setPayload(payload);
    return this;
  }

  @Override
  public ZeebeFuture<CreateJobResponse> send() {
    final CreateJobRequest request = builder.setCustomHeaders(toJson(customHeaders)).build();

    final ZeebeClientFutureImpl<CreateJobResponse, GatewayOuterClass.CreateJobResponse> future =
        new ZeebeClientFutureImpl<>(CreateJobResponseImpl::new);

    asyncStub.createJob(request, future);
    return future;
  }
}
