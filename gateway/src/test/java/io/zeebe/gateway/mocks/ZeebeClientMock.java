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
package io.zeebe.gateway.mocks;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientConfiguration;
import io.zeebe.gateway.api.clients.TopicClient;
import io.zeebe.gateway.api.commands.TopicsRequestStep1;
import io.zeebe.gateway.api.commands.TopologyRequestStep1;
import io.zeebe.gateway.api.record.ZeebeObjectMapper;
import io.zeebe.gateway.api.subscription.ManagementSubscriptionBuilderStep1;

public class ZeebeClientMock implements ZeebeClient {

  private boolean produceError = false;

  public ZeebeClientMock() {}

  public ZeebeClientMock(final boolean produceError) {
    this.produceError = produceError;
  }

  @Override
  public TopicClient topicClient() {
    return null;
  }

  @Override
  public ZeebeObjectMapper objectMapper() {
    return null;
  }

  @Override
  public TopicsRequestStep1 newTopicsRequest() {
    return null;
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestMock(produceError);
  }

  @Override
  public ManagementSubscriptionBuilderStep1 newManagementSubscription() {
    return null;
  }

  @Override
  public ZeebeClientConfiguration getConfiguration() {
    return null;
  }

  @Override
  public void close() {}
}
