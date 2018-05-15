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

import java.util.Scanner;
import java.util.function.Consumer;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.SubscriptionClient;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.subscription.TopicSubscription;

public class TypedEventLogger
{

    public static void main(String[] args)
    {
        final String brokerContactPoint = "127.0.0.1:51015";

        final ZeebeClient zeebeClient = ZeebeClient.newClient()
                .brokerContactPoint(brokerContactPoint)
                .create();

        System.out.println(String.format("> Connecting to %s", brokerContactPoint));

        final String topicName = "default-topic";

        System.out.println(String.format("> Open event subscription from topic '%s'", topicName));

        final Consumer<Record> logger = r ->
        {
            System.out.println(r.getMetadata());
            System.out.println(r);
            System.out.println();
        };

        final SubscriptionClient subscriptionClient = zeebeClient.topicClient(topicName).subscriptionClient();

        final TopicSubscription subscription =
            subscriptionClient
                .newTopicSubscription()
                .name("logger")
                .workflowInstanceEventHandler(logger::accept)
                .jobEventHandler(logger::accept)
                .incidentEventHandler(logger::accept)
                .startAtHeadOfTopic()
                .forcedStart()
                .open();

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
