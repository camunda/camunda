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
package io.zeebe.broker.job.processor;

import java.util.Iterator;
import org.agrona.collections.ArrayUtil;
import org.agrona.collections.Long2LongHashMap;

public class JobSubscriptions implements Iterable<JobSubscription> {

  protected JobSubscription[] subscriptions;
  protected Long2LongHashMap lookupTable = new Long2LongHashMap(-1);
  protected int totalCredits = 0;

  public JobSubscriptions(int initialCapacity) {
    this.subscriptions = new JobSubscription[initialCapacity];
  }

  public void addSubscription(JobSubscription subscription) {
    int insertIndex = -1;
    final int currentSubscriptionsLength = subscriptions.length;
    for (int i = 0; i < currentSubscriptionsLength; i++) {
      if (subscriptions[i] == null) {
        insertIndex = i;
        break;
      }
    }

    if (insertIndex < 0) {
      subscriptions = ArrayUtil.ensureCapacity(subscriptions, subscriptions.length * 2);
      insertIndex = currentSubscriptionsLength;
    }

    subscriptions[insertIndex] = subscription;
    lookupTable.put(subscription.getSubscriberKey(), insertIndex);
    totalCredits += subscription.getCredits();
  }

  public void removeSubscription(long subscriberKey) {
    final long idx = lookupTable.get(subscriberKey);
    if (idx >= 0) {
      remove((int) idx);
    }
  }

  protected void remove(int index) {
    final JobSubscription currentValue = subscriptions[index];
    if (currentValue != null) {
      subscriptions[index] = null;
      lookupTable.remove(currentValue.getSubscriberKey());
      totalCredits -= currentValue.getCredits();
    }
  }

  public boolean isEmpty() {
    return lookupTable.isEmpty();
  }

  @Override
  public SubscriptionIterator iterator() {
    return new SubscriptionIterator();
  }

  public void addCredits(long subscriberKey, int credits) {
    final long idx = lookupTable.get(subscriberKey);

    if (idx >= 0) {
      final JobSubscription subscription = subscriptions[(int) idx];
      subscription.setCredits(subscription.getCredits() + credits);
      totalCredits += credits;
    }
  }

  public int getTotalCredits() {
    return totalCredits;
  }

  public int size() {
    return lookupTable.size();
  }

  public class SubscriptionIterator implements Iterator<JobSubscription> {
    int index;

    public SubscriptionIterator() {
      reset();
    }

    @Override
    public boolean hasNext() {
      return findNext() < subscriptions.length;
    }

    @Override
    public JobSubscription next() {
      final int nextElementIdx = findNext();
      if (nextElementIdx < subscriptions.length) {
        index = nextElementIdx;
        return subscriptions[index];
      } else {
        return null;
      }
    }

    protected int findNext() {
      int currIndex = index;
      do {
        currIndex++;
      } while (currIndex < subscriptions.length && subscriptions[currIndex] == null);

      return currIndex;
    }

    @Override
    public void remove() {
      JobSubscriptions.this.remove(index);
    }

    public void reset() {
      index = -1;
    }
  }
}
