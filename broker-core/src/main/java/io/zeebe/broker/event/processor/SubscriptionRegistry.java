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
package io.zeebe.broker.event.processor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

public class SubscriptionRegistry {

  protected SubscriptionIterator iterator = new SubscriptionIterator();

  protected final Long2ObjectHashMap<TopicSubscriptionPushProcessor> subscriptionProcessorsByKey =
      new Long2ObjectHashMap<>();
  protected final Map<DirectBuffer, TopicSubscriptionPushProcessor> subscriptionProcessorsByName =
      new HashMap<>();

  public void addSubscription(TopicSubscriptionPushProcessor processor) {
    subscriptionProcessorsByKey.put(processor.getSubscriptionId(), processor);
    subscriptionProcessorsByName.put(processor.getName(), processor);
  }

  public TopicSubscriptionPushProcessor getProcessorByName(DirectBuffer name) {
    return subscriptionProcessorsByName.get(name);
  }

  public TopicSubscriptionPushProcessor removeProcessorByKey(long key) {
    final TopicSubscriptionPushProcessor processor = subscriptionProcessorsByKey.remove(key);
    if (processor != null) {
      subscriptionProcessorsByName.remove(processor.getName());
    }

    return processor;
  }

  /** This is not supposed to be used concurrently */
  public Iterator<TopicSubscriptionPushProcessor> iterateSubscriptions() {
    iterator.reset();
    return iterator;
  }

  protected class SubscriptionIterator implements Iterator<TopicSubscriptionPushProcessor> {
    protected Iterator<TopicSubscriptionPushProcessor> innerIterator;
    protected TopicSubscriptionPushProcessor currentValue = null;

    protected void reset() {
      innerIterator = subscriptionProcessorsByKey.values().iterator();
    }

    @Override
    public boolean hasNext() {
      return innerIterator.hasNext();
    }

    @Override
    public TopicSubscriptionPushProcessor next() {
      currentValue = innerIterator.next();
      return currentValue;
    }

    @Override
    public void remove() {
      innerIterator.remove();
      subscriptionProcessorsByName.remove(currentValue.getName());
    }
  }
}
