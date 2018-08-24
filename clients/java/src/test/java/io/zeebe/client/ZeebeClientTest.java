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
package io.zeebe.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionBrokerRole;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ZeebeClientTest {

  @Rule public EmbeddedBrokerRule rule = new EmbeddedBrokerRule();

  private ZeebeClient client;

  @Before
  public void setUp() {
    client =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(rule.getClientAddress().toString())
            .build();
  }

  @Test
  public void shouldGetHealthCheck() throws InterruptedException {
    Stream.generate(() -> client.newTopologyRequest().send())
        .limit(10)
        .map(ZeebeFuture::join)
        .forEach(
            response -> {
              assertThat(response).isNotNull();

              final BrokerInfo broker = response.getBrokers().get(0);
              assertThat(broker.getAddress()).isNotEmpty();
              assertThat(broker.getPartitions().size()).isEqualTo(1);

              broker
                  .getPartitions()
                  .forEach(
                      partition -> {
                        assertThat(partition.getPartitionId()).isEqualTo(0);
                        assertThat(partition.getTopicName()).isEqualTo("internal-system");
                        assertThat(partition.getRole()).isEqualTo(PartitionBrokerRole.LEADER);
                      });
            });
  }
}
