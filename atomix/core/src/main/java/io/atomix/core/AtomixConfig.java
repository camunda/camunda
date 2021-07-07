/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core;

import io.atomix.cluster.ClusterConfig;
import io.atomix.primitive.partition.PartitionGroupConfig;
import io.atomix.utils.config.Config;

/** Atomix configuration. */
public class AtomixConfig implements Config {

  private final ClusterConfig cluster = new ClusterConfig();
  private PartitionGroupConfig<?> partitionGroup;

  /**
   * Returns the cluster configuration.
   *
   * @return the cluster configuration
   */
  ClusterConfig getClusterConfig() {
    return cluster;
  }

  /**
   * Returns the partition group configurations.
   *
   * @return the partition group configurations
   */
  PartitionGroupConfig<?> getPartitionGroup() {
    return partitionGroup;
  }

  /**
   * Adds a partition group configuration.
   *
   * @param partitionGroup the partition group configuration to add
   * @return the Atomix configuration
   */
  AtomixConfig setPartitionGroup(final PartitionGroupConfig partitionGroup) {
    this.partitionGroup = partitionGroup;
    return this;
  }
}
