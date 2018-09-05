/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.topology;

import java.util.Objects;

public class PartitionInfo {
  private final int partitionId;
  private final int replicationFactor;

  public PartitionInfo(final int partitionId, final int replicationFactor) {
    this.partitionId = partitionId;
    this.replicationFactor = replicationFactor;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PartitionInfo that = (PartitionInfo) o;
    return partitionId == that.partitionId && replicationFactor == that.replicationFactor;
  }

  @Override
  public int hashCode() {
    return Objects.hash(partitionId, replicationFactor);
  }

  @Override
  public String toString() {
    return "PartitionInfo{"
        + "partitionId="
        + partitionId
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
