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

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.ActivateJobsCommandStep1;
import io.zeebe.client.api.commands.ActivateJobsCommandStep1.ActivateJobsCommandStep2;
import io.zeebe.client.api.commands.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.client.impl.ZeebeStreamingClientFutureImpl;
import io.zeebe.client.impl.response.ActivateJobsResponseImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest.Builder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class ActivateJobsCommandImpl
    implements ActivateJobsCommandStep1, ActivateJobsCommandStep2, ActivateJobsCommandStep3 {

  private final GatewayStub asyncStub;
  private final ZeebeObjectMapper objectMapper;
  private final Builder builder;

  public ActivateJobsCommandImpl(
      GatewayStub asyncStub, ZeebeClientConfiguration config, ZeebeObjectMapper objectMapper) {
    this.asyncStub = asyncStub;
    this.objectMapper = objectMapper;
    builder = ActivateJobsRequest.newBuilder();

    timeout(config.getDefaultJobTimeout());
    workerName(config.getDefaultJobWorkerName());
  }

  @Override
  public ActivateJobsCommandStep2 jobType(String jobType) {
    builder.setType(jobType);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 maxJobsToActivate(int maxJobsToActivate) {
    builder.setMaxJobsToActivate(maxJobsToActivate);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 timeout(long timeout) {
    builder.setTimeout(timeout);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 timeout(Duration timeout) {
    return timeout(timeout.toMillis());
  }

  @Override
  public ActivateJobsCommandStep3 workerName(String workerName) {
    builder.setWorker(workerName);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 fetchVariables(List<String> fetchVariables) {
    builder.addAllFetchVariable(fetchVariables);
    return this;
  }

  @Override
  public ActivateJobsCommandStep3 fetchVariables(String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public ZeebeFuture<ActivateJobsResponse> send() {
    final ActivateJobsRequest request = builder.build();

    final ActivateJobsResponseImpl response = new ActivateJobsResponseImpl(objectMapper);
    final ZeebeStreamingClientFutureImpl<
            ActivateJobsResponse, io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse>
        future = new ZeebeStreamingClientFutureImpl<>(response, response::addResponse);

    asyncStub.activateJobs(request, future);
    return future;
  }
}
