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
package io.zeebe.gateway.impl.partitions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.gateway.api.commands.Partition;
import io.zeebe.gateway.api.commands.Partitions;
import java.util.List;

public class PartitionsImpl implements Partitions {
  private List<Partition> partitions;

  @JsonDeserialize(contentAs = PartitionImpl.class)
  public void setPartitions(final List<Partition> partitions) {
    this.partitions = partitions;
  }
  //
  //  public void setPartitions(final List<PartitionImpl> partitions) {
  //    this.partitions = new ArrayList<>();
  //    partitions.addAll(partitions);
  //  }

  @Override
  public List<Partition> getPartitions() {
    return partitions;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Partitions [");
    builder.append(partitions);
    builder.append("]");
    return builder.toString();
  }
}
