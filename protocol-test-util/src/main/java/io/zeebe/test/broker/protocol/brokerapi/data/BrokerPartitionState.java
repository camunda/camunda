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
package io.zeebe.test.broker.protocol.brokerapi.data;

public class BrokerPartitionState {
  public static final String LEADER_STATE = "LEADER";
  public static final String FOLLOWER_STATE = "FOLLOWER";

  private final String state;
  private final String topicName;
  private final int partitionId;

  public BrokerPartitionState(final String state, final String topicName, final int partitionId) {
    this.state = state;
    this.topicName = topicName;
    this.partitionId = partitionId;
  }

  public String getState() {
    return state;
  }

  public String getTopicName() {
    return topicName;
  }

  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public String toString() {
    return "BrokerPartitionState{"
        + "state='"
        + state
        + '\''
        + ", topicName='"
        + topicName
        + '\''
        + ", partitionId="
        + partitionId
        + '}';
  }
}
