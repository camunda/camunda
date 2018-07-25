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
import io.zeebe.client.api.clients.*;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.subscription.SubscriptionManager;
import io.zeebe.client.impl.subscription.topic.TopicSubscriptionBuilderImpl;
import io.zeebe.util.EnsureUtil;

public class TopicClientImpl implements TopicClient {
  private final ZeebeClientImpl client;
  private final String topic;

  public TopicClientImpl(ZeebeClientImpl client, String topic) {
    EnsureUtil.ensureNotNullOrEmpty("topic name", topic);

    this.client = client;
    this.topic = topic;
  }

  @Override
  public WorkflowClient workflowClient() {
    return new WorkflowsClientImpl(this);
  }

  @Override
  public JobClient jobClient() {
    return new JobClientImpl(this);
  }

  @Override
  public TopicSubscriptionBuilderStep1 newSubscription() {
    return new TopicSubscriptionBuilderImpl(this);
  }

  public String getTopic() {
    return topic;
  }

  public RequestManager getCommandManager() {
    return client.getCommandManager();
  }

  public ZeebeObjectMapperImpl getObjectMapper() {
    return client.getObjectMapper();
  }

  public SubscriptionManager getSubscriptionManager() {
    return client.getSubscriptionManager();
  }

  public ZeebeClientConfiguration getConfiguration() {
    return client.getConfiguration();
  }
}
