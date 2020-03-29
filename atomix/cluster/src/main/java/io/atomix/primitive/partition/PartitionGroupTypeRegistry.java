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

import java.util.Collection;

/** Partition group type registry. */
public interface PartitionGroupTypeRegistry {

  /**
   * Returns the collection of partition group type configurations.
   *
   * @return the collection of partition group type configurations
   */
  Collection<PartitionGroup.Type> getGroupTypes();

  /**
   * Returns the partition group type with the given name.
   *
   * @param name the partition group type name
   * @return the group type
   */
  PartitionGroup.Type getGroupType(String name);
}
