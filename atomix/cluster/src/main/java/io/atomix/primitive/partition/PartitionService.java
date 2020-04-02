/*
 * Copyright 2017-present Open Networking Foundation
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

import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.ProxyProtocol;
import java.util.Collection;

/** Partition service. */
public interface PartitionService {

  /**
   * Returns the system partition group.
   *
   * @return the system partition group
   */
  PartitionGroup getSystemPartitionGroup();

  /**
   * Returns a partition group by name.
   *
   * @param name the name of the partition group
   * @return the partition group
   */
  PartitionGroup getPartitionGroup(String name);

  /**
   * Returns the first partition group that matches the given primitive type.
   *
   * @param type the primitive type
   * @return the first partition group that matches the given primitive type
   */
  @SuppressWarnings("unchecked")
  default PartitionGroup getPartitionGroup(final PrimitiveProtocol.Type type) {
    return getPartitionGroups().stream()
        .filter(group -> group.protocol().name().equals(type.name()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns the first partition group that matches the given primitive protocol.
   *
   * @param protocol the primitive protocol
   * @return the first partition group that matches the given primitive protocol
   */
  @SuppressWarnings("unchecked")
  default PartitionGroup getPartitionGroup(final ProxyProtocol protocol) {
    if (protocol.group() != null) {
      final PartitionGroup group = getPartitionGroup(protocol.group());
      if (group != null) {
        return group;
      }
      final PartitionGroup systemGroup = getSystemPartitionGroup();
      if (systemGroup != null && systemGroup.name().equals(protocol.group())) {
        return systemGroup;
      }
      return null;
    }

    for (final PartitionGroup partitionGroup : getPartitionGroups()) {
      if (partitionGroup.protocol().name().equals(protocol.type().name())) {
        return partitionGroup;
      }
    }
    return null;
  }

  /**
   * Returns a collection of all partition groups.
   *
   * @return a collection of all partition groups
   */
  Collection<PartitionGroup> getPartitionGroups();
}
