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
package io.zeebe.topic;

import java.util.Properties;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.TopicEvent;

public class CreateTopic
{

    public static void main(final String[] args)
    {
        final String broker = "localhost:51015";
        final String topic = "default-topic";
        final int partitions = 1;

        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, broker);

        try (ZeebeClient client = ZeebeClient.create(clientProperties))
        {
            System.out.println("Creating topic " + topic + " with " + partitions + " partition(s) with contact point " + broker);

            final TopicEvent topicEvent = client.newCreateTopicCommand()
                .name(topic)
                .partitions(partitions)
                .replicationFactor(1)
                .send()
                .join();

            System.out.println(topicEvent.getState());
        }
    }
}
