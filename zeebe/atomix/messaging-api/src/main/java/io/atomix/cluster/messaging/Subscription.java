/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging;

import java.util.concurrent.CompletableFuture;

/**
 * {@link ClusterEventService} subscription context.
 *
 * <p>The subscription represents a node's subscription to a specific topic. A {@code Subscription}
 * instance is returned once an {@link ClusterEventService} subscription has been propagated. The
 * subscription context can be used to unsubscribe the node from the given {@link #topic()} by
 * calling {@link #close()}.
 */
public interface Subscription {

  /**
   * Returns the subscription topic.
   *
   * @return the topic to which the subscriber is subscribed
   */
  String topic();

  /**
   * Closes the subscription, causing it to be unregistered.
   *
   * <p>When the subscription is closed, the subscriber will be unregistered and the change will be
   * propagated to all the members of the cluster. The returned future will be completed once the
   * change has been propagated to all nodes.
   *
   * @return a future to be completed once the subscription has been closed
   */
  CompletableFuture<Void> close();
}
