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

import io.atomix.cluster.MemberId;
import java.util.Collection;

/** Atomix partition. */
public interface Partition {

  /**
   * Returns the partition identifier.
   *
   * @return the partition identifier
   */
  PartitionId id();

  /**
   * Returns the partition term.
   *
   * @return the partition term
   */
  long term();

  /**
   * Returns the collection of all members in the partition.
   *
   * @return the collection of all members in the partition
   */
  Collection<MemberId> members();

  /**
   * Returns the partition's current primary.
   *
   * @return the partition's current primary
   */
  MemberId primary();

  /**
   * Returns the partition's backups.
   *
   * @return the partition's backups
   */
  Collection<MemberId> backups();

  /**
   * Returns the partition client.
   *
   * @return the partition client
   */
  PartitionClient getClient();
}
