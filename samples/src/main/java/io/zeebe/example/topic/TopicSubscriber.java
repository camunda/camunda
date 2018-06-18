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
package io.zeebe.example.topic;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.subscription.TopicSubscription;
import java.util.Scanner;

public class TopicSubscriber {

  public static void main(String[] args) {
    final String broker = "127.0.0.1:51015";
    final String topic = "default-topic";

    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder().brokerContactPoint(broker);

    try (ZeebeClient client = builder.build()) {
      final TopicClient topicClient = client.topicClient(topic);

      System.out.println("Opening topic subscription.");

      final TopicSubscription subscription =
          topicClient
              .newSubscription()
              .name("record-logger")
              .recordHandler(
                  record -> {
                    final RecordMetadata metadata = record.getMetadata();
                    System.out.println(
                        String.format(
                            "[topic: %d, position: %d, key: %d, type: %s, intent: %s]\n%s\n===",
                            metadata.getPartitionId(),
                            metadata.getPosition(),
                            metadata.getKey(),
                            metadata.getValueType(),
                            metadata.getIntent(),
                            record.toJson()));
                  })
              .startAtHeadOfTopic()
              .forcedStart()
              .open();

      System.out.println("Subscription opened and receiving records.");

      // call subscription.close() to close it

      // run until System.in receives exit command
      waitUntilSystemInput("exit");
    }
  }

  private static void waitUntilSystemInput(final String exitCode) {
    try (Scanner scanner = new Scanner(System.in)) {
      while (scanner.hasNextLine()) {
        final String nextLine = scanner.nextLine();
        if (nextLine.contains(exitCode)) {
          return;
        }
      }
    }
  }
}
