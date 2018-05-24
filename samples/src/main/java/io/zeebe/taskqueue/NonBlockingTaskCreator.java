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

import java.util.Properties;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;

public class NonBlockingTaskCreator
{
    private static final String SAMPLE_NUMBER_OF_REQUESTS = "sample.numberOfRequests";

    public static void main(String[] args)
    {
        final Properties systemProperties = System.getProperties();

        final int tasks = Integer.parseInt(systemProperties.getProperty(SAMPLE_NUMBER_OF_REQUESTS, "1000000"));

        final String topicName = "test";

        try (ZeebeClient client = ZeebeClient.newClientBuilder().withProperties(System.getProperties()).build())
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

            while (tasksCreated < tasks)
            {
                jobClient.newCreateCommand()
                    .jobType("greeting")
                    .addCustomHeader("some", "value")
                    .payload(payload)
                    .send();
                tasksCreated++;
            }

            System.out.println("Took: " + (System.currentTimeMillis() - time));
        }
    }
}
