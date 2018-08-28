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
package io.zeebe.broker.exporter.record.value;

import io.zeebe.broker.exporter.record.RecordValueImpl;
import io.zeebe.exporter.record.value.TopicRecordValue;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import java.util.List;
import java.util.Objects;

public class TopicRecordValueImpl extends RecordValueImpl implements TopicRecordValue {
  private final String name;
  private final List<Integer> partitionIds;
  private final int partitionCount;
  private final int replicationFactor;

  public TopicRecordValueImpl(
      final ZeebeObjectMapperImpl objectMapper,
      final String name,
      final List<Integer> partitionIds,
      final int partitionCount,
      final int replicationFactor) {
    super(objectMapper);
    this.name = name;
    this.partitionIds = partitionIds;
    this.partitionCount = partitionCount;
    this.replicationFactor = replicationFactor;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  @Override
  public int getPartitionCount() {
    return partitionCount;
  }

  @Override
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
    final TopicRecordValueImpl that = (TopicRecordValueImpl) o;
    return partitionCount == that.partitionCount
        && replicationFactor == that.replicationFactor
        && Objects.equals(name, that.name)
        && Objects.equals(partitionIds, that.partitionIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, partitionIds, partitionCount, replicationFactor);
  }

  @Override
  public String toString() {
    return "TopicRecordValueImpl{"
        + "name='"
        + name
        + '\''
        + ", partitionIds="
        + partitionIds
        + ", partitionCount="
        + partitionCount
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
