/**
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
import java.util.Scanner;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.impl.ZeebeClientImpl;

public class EventLogger
{

    public static void main(String[] args)
    {
        final String brokerContactPoint = "127.0.0.1:51015";

        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, brokerContactPoint);

        final ZeebeClient zeebeClient = new ZeebeClientImpl(clientProperties);

        System.out.println(String.format("> Connecting to %s ...", brokerContactPoint));
        zeebeClient.connect();

        System.out.println("> Connected.");

        final String topicName = "default-topic";
        final int partitionid = 0;

        System.out.println(String.format("> Open event subscription from topic '%s' and partition '%d'", topicName, partitionid));

        final TopicSubscription subscription = zeebeClient.topic(topicName, partitionid)
            .newSubscription()
            .startAtHeadOfTopic()
            .forcedStart()
            .name("logger")
            .handler((meta, event) ->
            {
                System.out.println(String.format(">>> [topic: %d, position: %d, key: %d, type: %s]\n%s\n===",
                        meta.getPartitionId(),
                        meta.getEventPosition(),
                        meta.getEventKey(),
                        meta.getEventType(),
                        event.getJson()));
            })
            .open();

        System.out.println("> Opened.");

        // wait for events
        try (Scanner scanner = new Scanner(System.in))
        {
            while (scanner.hasNextLine())
            {
                final String nextLine = scanner.nextLine();
                if (nextLine.contains("exit"))
                {
                    System.out.println("> Closing...");

                    subscription.close();
                    zeebeClient.close();

                    System.out.println("> Closed.");

                    System.exit(0);
                }
            }
        }
    }

}
