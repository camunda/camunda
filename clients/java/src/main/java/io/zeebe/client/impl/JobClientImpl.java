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

import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.CompleteJobCommandStep1;
import io.zeebe.client.api.commands.CreateJobCommandStep1;
import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.api.commands.UpdateRetriesJobCommandStep1;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1;
import io.zeebe.client.impl.job.CompleteJobCommandImpl;
import io.zeebe.client.impl.job.CreateJobCommandImpl;
import io.zeebe.client.impl.job.FailJobCommandImpl;
import io.zeebe.client.impl.job.JobUpdateRetriesCommandImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;

public class JobClientImpl implements JobClient {

  private final GatewayStub asyncStub;
  private final ZeebeObjectMapper objectMapper;

  public JobClientImpl(GatewayStub asyncStub, ZeebeObjectMapper objectMapper) {
    this.asyncStub = asyncStub;
    this.objectMapper = objectMapper;
  }

  @Override
  public CreateJobCommandStep1 newCreateCommand() {
    return new CreateJobCommandImpl(asyncStub, objectMapper);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(long jobKey) {
    return new CompleteJobCommandImpl(asyncStub, objectMapper, jobKey);
  }

  @Override
  public FailJobCommandStep1 newFailCommand(long jobKey) {
    return new FailJobCommandImpl(asyncStub, jobKey);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(long jobKey) {
    return new JobUpdateRetriesCommandImpl(asyncStub, jobKey);
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    return null;
  }
}
