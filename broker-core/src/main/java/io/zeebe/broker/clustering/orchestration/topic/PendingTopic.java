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
package io.zeebe.broker.clustering.orchestration.topic;

import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public class PendingTopic {
  private final String topicName;
  private final DirectBuffer topicNameBuffer;
  private final int partitionCount;
  private final int replicationFactor;
  private final List<Integer> partitionIds;
  private final int missingPartitions;
  private final long key;

  PendingTopic(
      final DirectBuffer topicNameBuffer,
      final int partitionCount,
      final int replicationFactor,
      final List<Integer> partitionIds,
      final int missingPartitions,
      final long key) {
    this.topicName = BufferUtil.bufferAsString(topicNameBuffer);
    this.topicNameBuffer = BufferUtil.cloneBuffer(topicNameBuffer);
    this.partitionCount = partitionCount;
    this.replicationFactor = replicationFactor;
    this.partitionIds = partitionIds;
    this.missingPartitions = missingPartitions;
    this.key = key;
  }

  public String getTopicName() {
    return topicName;
  }

  public DirectBuffer getTopicNameBuffer() {
    return topicNameBuffer;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  public int getMissingPartitions() {
    return missingPartitions;
  }

  public long getKey() {
    return key;
  }

  @Override
  public String toString() {
    return "PendingTopic{"
        + "topicName='"
        + topicName
        + '\''
        + ", topicNameBuffer="
        + topicNameBuffer
        + ", partitionCount="
        + partitionCount
        + ", replicationFactor="
        + replicationFactor
        + ", partitionIds="
        + partitionIds
        + ", missingPartitions="
        + missingPartitions
        + ", key="
        + key
        + '}';
  }
}
