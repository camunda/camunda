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
package io.zeebe.gateway.impl.clustering;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.gateway.api.commands.BrokerInfo;
import io.zeebe.gateway.api.commands.Topology;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import java.util.ArrayList;
import java.util.List;

// TODO: remove with https://github.com/zeebe-io/zeebe/issues/1377
public class TopologyImpl implements Topology {
  private List<BrokerInfo> brokers = new ArrayList<>();
  private int clusterSize;
  private int partitionsCount;

  public TopologyImpl() {}

  public TopologyImpl(TopologyResponseDto dto) {
    this.brokers = new ArrayList<>();
    dto.brokers().forEach(broker -> brokers.add(new BrokerInfoImpl(broker)));
  }

  @Override
  public List<BrokerInfo> getBrokers() {
    return brokers;
  }

  @JsonDeserialize(contentAs = BrokerInfoImpl.class)
  public void setBrokers(List<BrokerInfo> brokers) {
    this.brokers = brokers;
  }

  public int getClusterSize() {
    return clusterSize;
  }

  public void setClusterSize(int clusterSize) {
    this.clusterSize = clusterSize;
  }

  public int getPartitionsCount() {
    return partitionsCount;
  }

  public void setPartitionsCount(int partitionsCount) {
    this.partitionsCount = partitionsCount;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Topology [brokers=");
    builder.append(brokers);
    builder.append("]");
    return builder.toString();
  }
}
