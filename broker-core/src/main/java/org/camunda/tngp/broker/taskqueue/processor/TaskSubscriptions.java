/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.taskqueue.processor;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.camunda.tngp.util.buffer.BufferUtil;

public class TaskSubscriptions
{
    protected Long2ObjectHashMap<TaskSubscription> subscriptionsById = new Long2ObjectHashMap<>();
    protected Int2ObjectHashMap<TaskSubscriptionBucket> subscriptionsByTypeHash = new Int2ObjectHashMap<>();

    protected int availableCredits = 0;

    public void addSubscription(final TaskSubscription subscription)
    {
        subscriptionsById.put(subscription.getId(), subscription);

        final int typeHash = typeHash(subscription.getLockTaskType());
        TaskSubscriptionBucket subscriptionBucket = subscriptionsByTypeHash.get(typeHash);
        if (subscriptionBucket == null)
        {
            subscriptionBucket = new TaskSubscriptionBucket();
            subscriptionsByTypeHash.put(typeHash, subscriptionBucket);
        }
        subscriptionBucket.add(subscription.getId());

        availableCredits += subscription.getCredits();
    }

    public void removeSubscription(long id)
    {
        final TaskSubscription subscription = subscriptionsById.remove(id);

        if (subscription != null)
        {
            final int typeHashCode = typeHash(subscription.getLockTaskType());
            final TaskSubscriptionBucket subscriptionBucket = subscriptionsByTypeHash.get(typeHashCode);
            subscriptionBucket.remove(id);

            availableCredits -= subscription.getCredits();
        }
    }

    public TaskSubscription getNextAvailableSubscription(final DirectBuffer typeBuffer)
    {
        TaskSubscription nextSubscription = null;

        if (hasSubscriptionsWithCredits())
        {
            final int typeHashCode = typeHash(typeBuffer);
            final TaskSubscriptionBucket subscriptionBucket = subscriptionsByTypeHash.get(typeHashCode);
            if (subscriptionBucket != null)
            {
                nextSubscription = subscriptionBucket.getNextAvailableSubscription(typeBuffer);
            }
        }
        return nextSubscription;
    }

    public boolean updateSubscriptionCredits(long id, int credits)
    {
        boolean updated = false;

        final TaskSubscription subscription = subscriptionsById.get(id);
        if (subscription != null)
        {
            availableCredits += credits - subscription.getCredits();

            subscription.setCredits(credits);
            updated = true;
        }

        return updated;
    }

    public boolean hasSubscriptionsWithCredits()
    {
        return availableCredits > 0;
    }

    public boolean isEmpty()
    {
        return subscriptionsById.isEmpty();
    }

    protected int typeHash(final DirectBuffer buffer)
    {
        int result = 1;

        for (int i = 0; i < buffer.capacity(); i++)
        {
            result = 31 * result + buffer.getByte(i);
        }

        return result;
    }

    class TaskSubscriptionBucket
    {
        protected List<Long> subscriptionIds = new ArrayList<>();
        protected int nextElement = 0;

        public void add(long id)
        {
            subscriptionIds.add(id);
        }

        public void remove(long id)
        {
            subscriptionIds.remove(id);

            final int subscriptionSize = subscriptionIds.size();
            if (subscriptionSize > 0)
            {
                nextElement = Math.min(nextElement, subscriptionSize - 1);
            }
        }

        public TaskSubscription getNextAvailableSubscription(final DirectBuffer typeBuffer)
        {
            TaskSubscription nextSubscription = null;

            final int subscriptionSize = subscriptionIds.size();
            if (subscriptionSize > 0)
            {
                int index = nextElement;
                do
                {
                    final long subscriptId = subscriptionIds.get(index);
                    final TaskSubscription subscription = subscriptionsById.get(subscriptId);

                    if (subscription.getCredits() > 0 && BufferUtil.equals(subscription.getLockTaskType(), typeBuffer))
                    {
                        nextSubscription = subscription;
                    }

                    index = (index + 1) % subscriptionSize;

                } while (nextSubscription == null && index != nextElement);

                nextElement = index;
            }
            return nextSubscription;
        }
    }

}
