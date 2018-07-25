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
package io.zeebe.client.impl.subscription;

import org.agrona.concurrent.Agent;

public class SubscriptionExecutor implements Agent {
  public static final String ROLE_NAME = "subscription-executor";

  protected final SubscriberGroups topicSubscriptions;
  protected final SubscriberGroups taskSubscriptions;

  public SubscriptionExecutor(
      SubscriberGroups topicSubscriptions, SubscriberGroups taskSubscriptions) {
    this.topicSubscriptions = topicSubscriptions;
    this.taskSubscriptions = taskSubscriptions;
  }

  @Override
  public int doWork() throws Exception {
    int workCount = topicSubscriptions.pollSubscribers();
    workCount += taskSubscriptions.pollSubscribers();
    return workCount;
  }

  @Override
  public String roleName() {
    return ROLE_NAME;
  }
}
