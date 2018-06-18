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

import io.zeebe.client.impl.Loggers;
import io.zeebe.transport.RemoteAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;

@SuppressWarnings("rawtypes")
public class SubscriberGroups {
  protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

  // partitionId => subscriberKey => subscription (subscriber keys are not guaranteed to be globally
  // unique)
  protected Int2ObjectHashMap<Long2ObjectHashMap<Subscriber>> subscribers =
      new Int2ObjectHashMap<>();

  protected final List<SubscriberGroup> subscriberGroups = new CopyOnWriteArrayList<>();

  public void addGroup(final SubscriberGroup group) {
    this.subscriberGroups.add(group);
  }

  public void closeAllGroups(String reason) {
    subscriberGroups.forEach(g -> g.initClose(reason, null));
  }

  public void add(Subscriber subscriber) {
    this.subscribers
        .computeIfAbsent(subscriber.getPartitionId(), partitionId -> new Long2ObjectHashMap<>())
        .put(subscriber.getSubscriberKey(), subscriber);
  }

  public void remove(final Subscriber subscriber) {
    final int partitionId = subscriber.getPartitionId();

    final Long2ObjectHashMap<Subscriber> subscribersForPartition = subscribers.get(partitionId);
    if (subscribersForPartition != null) {
      subscribersForPartition.remove(subscriber.getSubscriberKey());

      if (subscribersForPartition.isEmpty()) {
        subscribers.remove(partitionId);
      }
    }
  }

  public void removeGroup(SubscriberGroup group) {
    subscriberGroups.remove(group);
  }

  public Subscriber getSubscriber(final int partitionId, final long subscriberKey) {
    final Long2ObjectHashMap<Subscriber> subscribersForPartition = subscribers.get(partitionId);

    if (subscribersForPartition != null) {
      return subscribersForPartition.get(subscriberKey);
    }

    return null;
  }

  public void reopenSubscribersForRemote(RemoteAddress remote) {
    subscriberGroups.forEach(g -> g.reopenSubscriptionsForRemoteAsync(remote));
  }

  private int forAllDo(List<SubscriberGroup> groups, ToIntFunction<SubscriberGroup> action) {
    int workCount = 0;

    for (SubscriberGroup group : groups) {
      workCount += action.applyAsInt(group);
    }

    return workCount;
  }

  public int pollSubscribers() {
    return forAllDo(subscriberGroups, s -> s.poll());
  }

  public boolean isAnySubscriberOpeningOn(int partitionId) {
    for (SubscriberGroup group : subscriberGroups) {
      if (group.isSubscribingTo(partitionId)) {
        return true;
      }
    }

    return false;
  }
}
