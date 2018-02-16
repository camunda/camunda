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
package io.zeebe.util.sched.channel;

import java.util.Arrays;

import io.zeebe.util.sched.ActorCondition;

public class ActorConditions
{
    private ActorCondition[] consumers = new ActorCondition[0];

    public void signalConsumers()
    {
        final ActorCondition[] consumer = consumers; // please do not remove me, array ref may be replaced concurrently

        for (int i = 0; i < consumer.length; i++)
        {
            consumer[i].signal();
        }
    }

    public synchronized void registerConsumer(ActorCondition listener)
    {
        consumers = appendToArray(consumers, listener);
    }

    public synchronized void removeConsumer(ActorCondition listener)
    {
        consumers = removeFromArray(consumers, listener);
    }

    private static ActorCondition[] appendToArray(ActorCondition[] array, ActorCondition listener)
    {
        array = Arrays.copyOf(array, array.length + 1);
        array[array.length - 1] = listener;
        return array;
    }

    private static ActorCondition[] removeFromArray(ActorCondition[] array, ActorCondition listener)
    {
        final int length = array.length;

        int index = -1;
        for (int i = 0; i < array.length; i++)
        {
            if (array[i] ==  listener)
            {
                index = i;
            }
        }

        final ActorCondition[] result = new ActorCondition[length - 1];
        System.arraycopy(array, 0, result, 0, index);
        if (index < length - 1)
        {
            System.arraycopy(array, index + 1, result, index, length - index - 1);
        }

        return result;
    }

}
