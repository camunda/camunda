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
package io.zeebe.gateway.impl.broker.cluster;

import static io.zeebe.transport.ClientTransport.UNKNOWN_NODE_ID;

import io.zeebe.transport.ClientTransport;
import java.util.List;

public interface BrokerClusterState {

  int NODE_ID_NULL = UNKNOWN_NODE_ID - 1;
  int PARTITION_ID_NULL = NODE_ID_NULL - 1;

  int getClusterSize();

  int getPartitionsCount();

  int getReplicationFactor();

  int getLeaderForPartition(int partition);

  List<Integer> getFollowersForPartition(int partition);

  /**
   * @return the node id of a random broker or {@link ClientTransport#UNKNOWN_NODE_ID} if no brokers
   *     are known
   */
  int getRandomBroker();

  List<Integer> getPartitions();

  List<Integer> getBrokers();

  String getBrokerAddress(int brokerId);

  int getPartition(int index);
}
