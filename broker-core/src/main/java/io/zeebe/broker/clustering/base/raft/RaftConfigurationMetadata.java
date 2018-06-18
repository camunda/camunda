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

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.util.ByteValue;
import java.util.ArrayList;
import java.util.List;

public class RaftConfigurationMetadata {
  private String topicName;
  private int partitionId;
  private int replicationFactor;
  private int term;
  private String votedForHost;
  private int votedForPort;

  @JsonProperty("segmentSize")
  private long logSegmentSize;

  private List<RaftConfigurationMetadataMember> members;

  public RaftConfigurationMetadata() {
    topicName = "";
    partitionId = -1;
    replicationFactor = -1;
    logSegmentSize = ByteValue.ofMegabytes(512).toBytes();
    term = 0;
    votedForHost = "";
    votedForPort = 0;
    members = new ArrayList<>();
  }

  public String getTopicName() {
    return topicName;
  }

  public void setTopicName(final String topicName) {
    ensureGreaterThan("Topic name length", topicName.length(), 0);
    this.topicName = topicName;
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

  public int getTerm() {
    return term;
  }

  public void setTerm(final int term) {
    this.term = term;
  }

  public int getVotedForPort() {
    return votedForPort;
  }

  public String getVotedForHost() {
    return votedForHost;
  }

  public void setVotedForHost(final String votedForHost) {
    this.votedForHost = votedForHost;
  }

  public void setVotedForPort(final int votedForPort) {
    this.votedForPort = votedForPort;
  }

  public List<RaftConfigurationMetadataMember> getMembers() {
    return members;
  }

  public void setMembers(final List<RaftConfigurationMetadataMember> members) {
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
    setTerm(source.getTerm());
    setTopicName(source.getTopicName());
    setMembers(source.getMembers());
    setVotedForHost(source.getVotedForHost());
    setVotedForPort(source.getVotedForPort());
  }
}
