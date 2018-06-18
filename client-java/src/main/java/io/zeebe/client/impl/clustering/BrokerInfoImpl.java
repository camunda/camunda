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
package io.zeebe.client.impl.clustering;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionInfo;
import java.util.ArrayList;
import java.util.List;

public class BrokerInfoImpl implements BrokerInfo {
  private String host;
  private int port;

  private List<PartitionInfo> partitions = new ArrayList<>();

  public BrokerInfoImpl setHost(final String host) {
    this.host = host;
    return this;
  }

  public BrokerInfoImpl setPort(final int port) {
    this.port = port;
    return this;
  }

  @Override
  public List<PartitionInfo> getPartitions() {
    return partitions;
  }

  @JsonDeserialize(contentAs = PartitionInfoImpl.class)
  public void setPartitions(List<PartitionInfo> partitions) {
    this.partitions = partitions;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public String getAddress() {
    return String.format("%s:%d", host, port);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("BrokerInfo [host=");
    builder.append(host);
    builder.append(", port=");
    builder.append(port);
    builder.append(", partitions=");
    builder.append(partitions);
    builder.append("]");
    return builder.toString();
  }
}
