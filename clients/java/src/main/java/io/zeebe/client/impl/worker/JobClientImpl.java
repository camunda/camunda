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
import io.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.client.impl.command.CompleteJobCommandImpl;
import io.zeebe.client.impl.command.FailJobCommandImpl;
import io.zeebe.client.impl.command.ThrowErrorCommandImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import java.util.function.Predicate;

public final class JobClientImpl implements JobClient {

  private final GatewayStub asyncStub;
  private final ZeebeClientConfiguration config;
  private final ZeebeObjectMapper objectMapper;
  private final Predicate<Throwable> retryPredicate;

  public JobClientImpl(
      final GatewayStub asyncStub,
      final ZeebeClientConfiguration config,
      final ZeebeObjectMapper objectMapper,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.config = config;
    this.objectMapper = objectMapper;
    this.retryPredicate = retryPredicate;
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(final long jobKey) {
    return new CompleteJobCommandImpl(
        asyncStub, objectMapper, jobKey, config.getDefaultRequestTimeout(), retryPredicate);
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(ActivatedJob job) {
    return new CompleteJobCommandImpl(
        asyncStub, objectMapper, job.getKey(), config.getDefaultRequestTimeout(), retryPredicate);
  }

  @Override
  public FailJobCommandStep1 newFailCommand(final long jobKey) {
    return new FailJobCommandImpl(
        asyncStub, jobKey, config.getDefaultRequestTimeout(), retryPredicate);
  }

  @Override
  public ThrowErrorCommandStep1 newThrowErrorCommand(long jobKey) {
    return new ThrowErrorCommandImpl(
        asyncStub, jobKey, config.getDefaultRequestTimeout(), retryPredicate);
  }
}
