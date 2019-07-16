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
package io.zeebe.client.impl.worker;

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.command.CompleteJobCommandStep1;
import io.zeebe.client.api.command.FailJobCommandStep1;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.client.impl.command.CompleteJobCommandImpl;
import io.zeebe.client.impl.command.FailJobCommandImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;

public class JobClientImpl implements JobClient {

  private final GatewayStub asyncStub;
  private final ZeebeClientConfiguration config;
  private final ZeebeObjectMapper objectMapper;

  public JobClientImpl(
      GatewayStub asyncStub, ZeebeClientConfiguration config, ZeebeObjectMapper objectMapper) {
    this.asyncStub = asyncStub;
    this.config = config;
    this.objectMapper = objectMapper;
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(long jobKey) {
    return new CompleteJobCommandImpl(
        asyncStub, objectMapper, jobKey, config.getDefaultRequestTimeout());
  }

  @Override
  public FailJobCommandStep1 newFailCommand(long jobKey) {
    return new FailJobCommandImpl(asyncStub, jobKey, config.getDefaultRequestTimeout());
  }
}
