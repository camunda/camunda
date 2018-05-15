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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.cmd.ClientCommandRejectedException;

public class NonBlockingTaskCreator
{
    private static final String SAMPLE_NUMBER_OF_REQUESTS = "sample.numberOfRequests";

    public static void main(String[] args)
    {
        final Properties systemProperties = System.getProperties();

        final int tasks = Integer.parseInt(systemProperties.getProperty(SAMPLE_NUMBER_OF_REQUESTS, "1_000_000"));

        final String topicName = "default-topic";

        try (ZeebeClient client = ZeebeClient.create(System.getProperties()))
        {

            System.out.println("Client configuration: " + client.getConfiguration());

            try
            {
                // try to create default topic if it not exists already
                client.newCreateTopicCommand().name(topicName)
                    .partitions(4)
                    .replicationFactor(1)
                    .send()
                    .join();
            }
            catch (final ClientCommandRejectedException e)
            {
                // topic already exists
            }

            final JobClient jobClient = client.topicClient(topicName).jobClient();

            final String payload = "{}";

            final long time = System.currentTimeMillis();

            long tasksCreated = 0;

            final List<Future<JobEvent>> inFlightRequests = new LinkedList<>();

            while (tasksCreated < tasks)
            {

                final ZeebeFuture<JobEvent> responseFuture = jobClient
                    .newCreateCommand()
                    .jobType("greeting")
                    .addCustomHeader("some", "value")
                    .payload(payload)
                    .send();

                inFlightRequests.add(responseFuture);
                tasksCreated++;

                poll(inFlightRequests);
            }

            awaitAll(inFlightRequests);

            System.out.println("Took: " + (System.currentTimeMillis() - time));

        }

    }

    private static void awaitAll(List<Future<JobEvent>> inFlightRequests)
    {
        while (!inFlightRequests.isEmpty())
        {
            poll(inFlightRequests);
        }
    }

    private static void poll(List<Future<JobEvent>> inFlightRequests)
    {
        final Iterator<Future<JobEvent>> iterator = inFlightRequests.iterator();
        while (iterator.hasNext())
        {
            final Future<JobEvent> future = iterator.next();
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
