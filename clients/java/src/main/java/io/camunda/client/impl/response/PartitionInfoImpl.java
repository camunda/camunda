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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionBrokerRole;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.Partition.HealthEnum;
import io.camunda.client.protocol.rest.Partition.RoleEnum;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import java.util.Objects;

public class PartitionInfoImpl implements PartitionInfo {

  private final int partitionId;
  private final PartitionBrokerRole role;
  private final PartitionBrokerHealth partitionBrokerHealth;

  public PartitionInfoImpl(final Partition partition) {
    partitionId = partition.getPartitionId();

    if (partition.getRole() == Partition.PartitionBrokerRole.LEADER) {
      role = PartitionBrokerRole.LEADER;
    } else if (partition.getRole() == Partition.PartitionBrokerRole.FOLLOWER) {
      role = PartitionBrokerRole.FOLLOWER;
    } else if (partition.getRole() == Partition.PartitionBrokerRole.INACTIVE) {
      role = PartitionBrokerRole.INACTIVE;
    } else {
      EnumUtil.logUnknownEnumValue(
          partition.getRole(), "partition broker role", PartitionBrokerRole.values());
      role = PartitionBrokerRole.UNKNOWN_ENUM_VALUE;
    }

    if (partition.getHealth() == Partition.PartitionBrokerHealth.HEALTHY) {
      partitionBrokerHealth = PartitionBrokerHealth.HEALTHY;
    } else if (partition.getHealth() == Partition.PartitionBrokerHealth.UNHEALTHY) {
      partitionBrokerHealth = PartitionBrokerHealth.UNHEALTHY;
    } else if (partition.getHealth() == Partition.PartitionBrokerHealth.DEAD) {
      partitionBrokerHealth = PartitionBrokerHealth.DEAD;
    } else {
      EnumUtil.logUnknownEnumValue(
          partition.getHealth(), "partition broker health", PartitionBrokerHealth.values());
      partitionBrokerHealth = PartitionBrokerHealth.UNKNOWN_ENUM_VALUE;
    }
  }

  public PartitionInfoImpl(final io.camunda.client.protocol.rest.Partition httpPartition) {

    if (httpPartition.getPartitionId() == null) {
      throw new RuntimeException("Unexpected missing partition ID. A partition ID is required.");
    }
    partitionId = httpPartition.getPartitionId();

    if (httpPartition.getRole() == RoleEnum.LEADER) {
      role = PartitionBrokerRole.LEADER;
    } else if (httpPartition.getRole() == RoleEnum.FOLLOWER) {
      role = PartitionBrokerRole.FOLLOWER;
    } else if (httpPartition.getRole() == RoleEnum.INACTIVE) {
      role = PartitionBrokerRole.INACTIVE;
    } else {
      EnumUtil.logUnknownEnumValue(
          httpPartition.getRole(), "partition broker role", PartitionBrokerRole.values());
      role = PartitionBrokerRole.UNKNOWN_ENUM_VALUE;
    }

    if (httpPartition.getHealth() == HealthEnum.HEALTHY) {
      partitionBrokerHealth = PartitionBrokerHealth.HEALTHY;
    } else if (httpPartition.getHealth() == HealthEnum.UNHEALTHY) {
      partitionBrokerHealth = PartitionBrokerHealth.UNHEALTHY;
    } else if (httpPartition.getHealth() == HealthEnum.DEAD) {
      partitionBrokerHealth = PartitionBrokerHealth.DEAD;
    } else {
      EnumUtil.logUnknownEnumValue(
          httpPartition.getHealth(), "partition broker health", PartitionBrokerHealth.values());
      partitionBrokerHealth = PartitionBrokerHealth.UNKNOWN_ENUM_VALUE;
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
  public int hashCode() {
    return Objects.hash(partitionId, role, partitionBrokerHealth);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final PartitionInfoImpl that = (PartitionInfoImpl) o;
    return partitionId == that.partitionId
        && role == that.role
        && partitionBrokerHealth == that.partitionBrokerHealth;
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
