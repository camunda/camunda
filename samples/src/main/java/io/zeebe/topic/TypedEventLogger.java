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
import io.zeebe.client.event.Event;
import io.zeebe.client.event.TopicSubscription;

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

        final Consumer<Event> logger = e ->
        {
            System.out.println(e.getMetadata());
            System.out.println(e);
            System.out.println();
        };

        final TopicSubscription subscription =
            zeebeClient.topics()
                       .newSubscription(topicName)
                       .name("logger")
                       .startAtHeadOfTopic()
                       .forcedStart()
                       .workflowEventHandler(logger::accept)
                       .workflowInstanceEventHandler(logger::accept)
                       .taskEventHandler(logger::accept)
                       .incidentEventHandler(logger::accept)
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
