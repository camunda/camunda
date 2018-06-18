/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.job;

import io.zeebe.broker.job.processor.JobSubscription;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

public class Subscriptions {
  private final Long2ObjectHashMap<JobSubscription> subscriptions = new Long2ObjectHashMap<>();

  public void addSubscription(JobSubscription subscription) {
    subscriptions.put(subscription.getSubscriberKey(), subscription);
  }

  public void removeSubscription(long subscriberKey) {
    subscriptions.remove(subscriberKey);
  }

  public void removeSubscriptionsForPartition(int partitionId) {
    final Iterator<JobSubscription> iterator = subscriptions.values().iterator();

    while (iterator.hasNext()) {
      if (iterator.next().getPartitionId() == partitionId) {
        iterator.remove();
      }
    }
  }

  public JobSubscription getSubscription(long subscriberKey) {
    return subscriptions.get(subscriberKey);
  }

  public List<JobSubscription> getSubscriptionsForChannel(int channel) {
    return subscriptions
        .values()
        .stream()
        .filter(s -> s.getStreamId() == channel)
        .collect(Collectors.toList());
  }

  public int getSubscriptionsForPartitionAndType(int partition, DirectBuffer type) {
    return (int)
        subscriptions
            .values()
            .stream()
            .filter(s -> s.getPartitionId() == partition && type.equals(s.getJobType()))
            .count();
  }
}
