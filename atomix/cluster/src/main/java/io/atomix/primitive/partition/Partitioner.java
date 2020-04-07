/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.primitive.partition;

import java.util.List;

/**
 * Interface for mapping from an object to partition ID.
 *
 * @param <K> object type.
 */
@FunctionalInterface
public interface Partitioner<K> {

  /** Murmur 3 partitioner. */
  Partitioner<String> MURMUR3 = new Murmur3Partitioner();

  /**
   * Returns the partition ID to which the specified object maps.
   *
   * @param key the key to partition
   * @param partitions the list of partitions
   * @return partition identifier
   */
  PartitionId partition(K key, List<PartitionId> partitions);
}
