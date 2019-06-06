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
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.Partitioner;
import java.util.List;
import java.util.Optional;

public final class DistributedLogstreamName implements Partitioner<String> {
  private static final DistributedLogstreamName DEFAULT_INSTANCE;

  static {
    DEFAULT_INSTANCE = new DistributedLogstreamName();
  }

  private DistributedLogstreamName() {}

  public static String getPartitionKey(int partitionId) {
    return String.valueOf(partitionId);
  }

  public static int getPartitionId(String partitionKey) {
    return Integer.parseInt(partitionKey);
  }

  public static DistributedLogstreamName getInstance() {
    return DEFAULT_INSTANCE;
  }

  @Override
  public PartitionId partition(String key, List<PartitionId> partitions) {
    final int id = Integer.parseInt(key);
    final Optional<PartitionId> partitionId =
        partitions.stream().filter(p -> p.id() == id).findFirst();
    return partitionId.get();
  }
}
