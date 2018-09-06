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
package io.zeebe.gateway.partitions;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.Partition;
import io.zeebe.gateway.api.commands.Partitions;
import io.zeebe.gateway.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class RequestPartitionsTest {

  public StubBrokerRule brokerRule = new StubBrokerRule();
  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldRequestPartitions() {
    // given
    final List<Map<String, Object>> partitions = new ArrayList<>();
    partitions.add(buildPartition(1));
    partitions.add(buildPartition(2));
    partitions.add(buildPartition(3));

    brokerRule
        .onControlMessageRequest(
            r ->
                r.messageType() == ControlMessageType.REQUEST_PARTITIONS
                    && r.partitionId() == Protocol.SYSTEM_PARTITION)
        .respondWith()
        .data()
        .put("partitions", partitions)
        .done()
        .register();

    final ZeebeClient client = clientRule.getClient();

    // when
    final Partitions result = client.newPartitionsRequest().send().join();

    // then
    final List<Partition> returnedPartitions = result.getPartitions();
    assertThat(returnedPartitions).hasSize(3);

    assertThat(returnedPartitions).extracting(p -> p.getId()).containsExactly(1, 2, 3);

    final List<ControlMessageRequest> partitionRequests =
        brokerRule
            .getReceivedControlMessageRequests()
            .stream()
            .filter(r -> r.messageType() == ControlMessageType.REQUEST_PARTITIONS)
            .collect(Collectors.toList());

    assertThat(partitionRequests).hasSize(1);
    final ControlMessageRequest request = partitionRequests.get(0);
    assertThat(request.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
  }

  public Map<String, Object> buildPartition(final int id) {
    final Map<String, Object> partition = new HashMap<>();
    partition.put("id", id);

    return partition;
  }
}
