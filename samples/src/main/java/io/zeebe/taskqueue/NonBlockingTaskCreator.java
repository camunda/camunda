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
package io.zeebe.taskqueue;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.*;
import io.zeebe.client.task.cmd.CreateTaskCmd;

public class NonBlockingTaskCreator
{
    private static final String SAMPLE_MAX_CONCURRENT_REQUESTS = "sample.maxConcurrentRequests";
    private static final String SAMPLE_NUMBER_OF_REQUESTS = "sample.numberOfRequests";

    public static void main(String[] args)
    {
        final Properties properties = System.getProperties();

        ClientProperties.setDefaults(properties);

        properties.putIfAbsent(SAMPLE_NUMBER_OF_REQUESTS, "1000000");
        properties.putIfAbsent(SAMPLE_MAX_CONCURRENT_REQUESTS, "64");

        printProperties(properties);

        final int numOfRequets = Integer.parseInt(properties.getProperty(SAMPLE_NUMBER_OF_REQUESTS));
        final int maxConcurrentRequests = Integer.parseInt(properties.getProperty(SAMPLE_MAX_CONCURRENT_REQUESTS));

        final String topicName = "default-topic";
        final int partitionId = 0;

        try (ZeebeClient client = ZeebeClient.create(properties))
        {
            client.connect();

            final TaskTopicClient asyncTaskService = client.taskTopic(topicName, partitionId);

            final String payload = "{}";

            final long time = System.currentTimeMillis();

            long tasksCreated = 0;

            final List<Future<Long>> inFlightRequests = new LinkedList<>();

            while (tasksCreated < numOfRequets)
            {

                if (inFlightRequests.size() < maxConcurrentRequests)
                {
                    final CreateTaskCmd cmd = asyncTaskService
                            .create()
                            .taskType("greeting")
                            .addHeader("some", "value")
                            .payload(payload);

                    inFlightRequests.add(cmd.executeAsync());
                    tasksCreated++;
                }

                poll(inFlightRequests);
            }

            awaitAll(inFlightRequests);

            System.out.println("Took: " + (System.currentTimeMillis() - time));

        }

    }

    private static void awaitAll(List<Future<Long>> inFlightRequests)
    {
        while (!inFlightRequests.isEmpty())
        {
            poll(inFlightRequests);
        }
    }

    private static void poll(List<Future<Long>> inFlightRequests)
    {
        final Iterator<Future<Long>> iterator = inFlightRequests.iterator();
        while (iterator.hasNext())
        {
            final Future<Long> future = iterator.next();
            if (future.isDone())
            {
                try
                {
                    future.get();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                catch (ExecutionException e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    iterator.remove();
                }
            }
        }
    }

    private static void printProperties(Properties properties)
    {
        System.out.println("Client configuration:");

        final TreeMap<String, String> sortedProperties = new TreeMap<>();

        final Enumeration<?> propertyNames = properties.propertyNames();
        while (propertyNames.hasMoreElements())
        {
            final String key = (String) propertyNames.nextElement();
            final String value = properties.getProperty(key);
            sortedProperties.put(key, value);
        }

        for (Entry<String, String> property : sortedProperties.entrySet())
        {
            System.out.println(String.format("%s: %s", property.getKey(), property.getValue()));
        }

    }

}
