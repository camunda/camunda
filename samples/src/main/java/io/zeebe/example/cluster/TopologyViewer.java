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
package io.zeebe.example.cluster;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.commands.Topology;

public class TopologyViewer {

  public static void main(final String[] args) {
    final String broker = "127.0.0.1:26500";

    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder().brokerContactPoint(broker);

    try (ZeebeClient client = builder.build()) {
      System.out.println("Requesting topology with initial contact point " + broker);

      final Topology topology = client.newTopologyRequest().send().join();

      System.out.println("Topology:");
      topology
          .getBrokers()
          .forEach(
              b -> {
                System.out.println("    " + b.getAddress());
                b.getPartitions()
                    .forEach(
                        p ->
                            System.out.println(
                                "      " + p.getPartitionId() + " - " + p.getRole()));
              });

      System.out.println("Done.");
    }
  }
}
