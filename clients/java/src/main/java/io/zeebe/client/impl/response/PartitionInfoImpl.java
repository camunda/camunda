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
package io.zeebe.client.impl.response;

import io.zeebe.client.api.response.PartitionBrokerHealth;
import io.zeebe.client.api.response.PartitionBrokerRole;
import io.zeebe.client.api.response.PartitionInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import java.util.Arrays;

public class PartitionInfoImpl implements PartitionInfo {

  private final int partitionId;
  private final PartitionBrokerRole role;
  private final PartitionBrokerHealth partitionBrokerHealth;

  public PartitionInfoImpl(final GatewayOuterClass.Partition partition) {
    partitionId = partition.getPartitionId();

    if (partition.getRole() == GatewayOuterClass.Partition.PartitionBrokerRole.LEADER) {
      role = PartitionBrokerRole.LEADER;
    } else if (partition.getRole() == GatewayOuterClass.Partition.PartitionBrokerRole.FOLLOWER) {
      role = PartitionBrokerRole.FOLLOWER;
    } else if (partition.getRole() == Partition.PartitionBrokerRole.INACTIVE) {
      role = PartitionBrokerRole.INACTIVE;
    } else {
      throw new RuntimeException(
          String.format(
              "Unexpected partition broker role %s, should be one of %s",
              partition.getRole(), Arrays.toString(PartitionBrokerRole.values())));
    }
    if (partition.getHealth() == GatewayOuterClass.Partition.PartitionBrokerHealth.HEALTHY) {
      this.partitionBrokerHealth = PartitionBrokerHealth.HEALTHY;
    } else if (partition.getHealth()
        == GatewayOuterClass.Partition.PartitionBrokerHealth.UNHEALTHY) {
      this.partitionBrokerHealth = PartitionBrokerHealth.UNHEALTHY;
    } else {
      throw new RuntimeException(
          String.format(
              "Unexpected partition broker health %s, should be one of %s",
              partition.getHealth(), Arrays.toString(PartitionBrokerHealth.values())));
    }
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public PartitionBrokerRole getRole() {
    return role;
  }

  @Override
  public boolean isLeader() {
    return role == PartitionBrokerRole.LEADER;
  }

  @Override
  public PartitionBrokerHealth getHealth() {
    return partitionBrokerHealth;
  }

  @Override
  public String toString() {
    return "PartitionInfoImpl{"
        + "partitionId="
        + partitionId
        + ", role="
        + role
        + ", health="
        + partitionBrokerHealth
        + '}';
  }
}
