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

import java.util.Objects;

public class BrokerPartitionState {
  public static final String LEADER_STATE = "LEADER";
  public static final String FOLLOWER_STATE = "FOLLOWER";

  private final String state;
  private final int partitionId;
  private final int replicationFactor;

  public BrokerPartitionState(final String state, final int partitionId, int replicationFactor) {
    this.state = state;
    this.partitionId = partitionId;
    this.replicationFactor = replicationFactor;
  }

  public String getState() {
    return state;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BrokerPartitionState that = (BrokerPartitionState) o;
    return partitionId == that.partitionId
        && replicationFactor == that.replicationFactor
        && Objects.equals(state, that.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, partitionId, replicationFactor);
  }

  @Override
  public String toString() {
    return "BrokerPartitionState{"
        + "state='"
        + state
        + '\''
        + ", partitionId="
        + partitionId
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
