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

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.CompleteJobCommandStep1;
import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.impl.job.CompleteJobCommandImpl;
import io.zeebe.client.impl.job.FailJobCommandImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.util.CloseableSilently;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class JobClientImpl implements JobClient {

  private final GatewayStub asyncStub;
  private final ZeebeClientConfiguration config;
  private final ZeebeObjectMapper objectMapper;
  private final ScheduledExecutorService executorService;
  private final List<CloseableSilently> closeables;

  public JobClientImpl(
      GatewayStub asyncStub,
      ZeebeClientConfiguration config,
      ZeebeObjectMapper objectMapper,
      ScheduledExecutorService executorService,
      List<CloseableSilently> closeables) {
    this.asyncStub = asyncStub;
    this.config = config;
    this.objectMapper = objectMapper;
    this.executorService = executorService;
    this.closeables = closeables;
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(long jobKey) {
    return new CompleteJobCommandImpl(asyncStub, objectMapper, jobKey);
  }

  @Override
  public FailJobCommandStep1 newFailCommand(long jobKey) {
    return new FailJobCommandImpl(asyncStub, jobKey);
  }
}
