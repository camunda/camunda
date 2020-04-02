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
package io.atomix.primitive.partition;

import io.atomix.utils.config.NamedConfig;
import io.atomix.utils.config.TypedConfig;

/** Partition group configuration. */
public abstract class PartitionGroupConfig<C extends PartitionGroupConfig<C>>
    implements TypedConfig<PartitionGroup.Type>, NamedConfig<C> {
  private String name;
  private int partitions = getDefaultPartitions();

  @Override
  public String getName() {
    return name;
  }

  @Override
  @SuppressWarnings("unchecked")
  public C setName(final String name) {
    this.name = name;
    return (C) this;
  }

  /**
   * Returns the number of partitions in the group.
   *
   * @return the number of partitions in the group.
   */
  public int getPartitions() {
    return partitions;
  }

  /**
   * Sets the number of partitions in the group.
   *
   * @param partitions the number of partitions in the group
   * @return the partition group configuration
   */
  @SuppressWarnings("unchecked")
  public C setPartitions(final int partitions) {
    this.partitions = partitions;
    return (C) this;
  }

  /**
   * Returns the default number of partitions.
   *
   * <p>Partition group configurations should override this method to provide a default number of
   * partitions.
   *
   * @return the default number of partitions
   */
  protected int getDefaultPartitions() {
    return 1;
  }
}
