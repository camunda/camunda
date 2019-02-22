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
package io.zeebe.broker.clustering.base.raft;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.util.ByteValue;
import java.util.ArrayList;
import java.util.List;

public class RaftConfigurationMetadata {
  private int partitionId;
  private int replicationFactor;

  @JsonProperty("segmentSize")
  private long logSegmentSize;

  private List<Integer> members;

  public RaftConfigurationMetadata() {
    partitionId = -1;
    replicationFactor = -1;
    logSegmentSize = ByteValue.ofMegabytes(512).toBytes();
    members = new ArrayList<>();
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public void setReplicationFactor(int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public List<Integer> getMembers() {
    return members;
  }

  public void setMembers(final List<Integer> members) {
    this.members.clear();
    this.members.addAll(members);
  }

  public long getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(long logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public void copy(RaftConfigurationMetadata source) {
    setLogSegmentSize(source.getLogSegmentSize());
    setPartitionId(source.getPartitionId());
    setReplicationFactor(source.getReplicationFactor());
    setMembers(source.getMembers());
  }
}
