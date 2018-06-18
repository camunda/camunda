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

import io.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;

public class PartitionInfo {
  private final String topicName;
  private final DirectBuffer topicNameBuffer;
  private final int partitionId;
  private final int replicationFactor;

  public PartitionInfo(
      final DirectBuffer topicNameBuffer, final int partitionId, final int replicationFactor) {
    this.topicName = BufferUtil.bufferAsString(topicNameBuffer);
    this.topicNameBuffer = topicNameBuffer;
    this.partitionId = partitionId;
    this.replicationFactor = replicationFactor;
  }

  public String getTopicName() {
    return topicName;
  }

  public DirectBuffer getTopicNameBuffer() {
    return topicNameBuffer;
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
    return partitionId == that.partitionId
        && replicationFactor == that.replicationFactor
        && BufferUtil.equals(topicNameBuffer, that.topicNameBuffer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(topicNameBuffer, partitionId, replicationFactor);
  }

  @Override
  public String toString() {
    return String.format(
        "Partition{topic=%s, partitionId=%d, replicationFactor=%d}",
        topicName, partitionId, replicationFactor);
  }
}
