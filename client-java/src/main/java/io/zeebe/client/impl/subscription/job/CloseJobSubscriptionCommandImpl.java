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
package io.zeebe.client.impl.subscription.job;

import io.zeebe.client.impl.ControlMessageRequest;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class CloseJobSubscriptionCommandImpl extends ControlMessageRequest<Void> {
  private final JobSubscriptionImpl subscription;

  public CloseJobSubscriptionCommandImpl(
      final RequestManager commandManager, int partition, long subscriberKey) {
    super(commandManager, ControlMessageType.REMOVE_JOB_SUBSCRIPTION, partition, Void.class);
    this.subscription = new JobSubscriptionImpl();
    this.subscription.setSubscriberKey(subscriberKey);
  }

  @Override
  public Object getRequest() {
    return subscription;
  }
}
