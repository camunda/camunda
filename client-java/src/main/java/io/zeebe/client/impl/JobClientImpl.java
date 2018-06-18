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
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1;
import io.zeebe.client.impl.job.*;
import io.zeebe.client.impl.subscription.job.JobSubcriptionBuilder;

public class JobClientImpl implements JobClient {
  private final TopicClientImpl client;

  public JobClientImpl(TopicClientImpl client) {
    this.client = client;
  }

  @Override
  public CreateJobCommandStep1 newCreateCommand() {
    return new CreateJobCommandImpl(
        client.getCommandManager(), client.getObjectMapper(), client.getTopic());
  }

  @Override
  public CompleteJobCommandStep1 newCompleteCommand(JobEvent event) {
    return new CompleteJobCommandImpl(client.getCommandManager(), event);
  }

  @Override
  public FailJobCommandStep1 newFailCommand(JobEvent event) {
    return new FailJobCommandImpl(client.getCommandManager(), event);
  }

  @Override
  public UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(JobEvent event) {
    return new UpdateRetriesJobCommandImpl(client.getCommandManager(), event);
  }

  @Override
  public JobWorkerBuilderStep1 newWorker() {
    return new JobSubcriptionBuilder(client);
  }
}
