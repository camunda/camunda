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

import java.util.concurrent.CompletableFuture;

/** Managed partition group. */
public interface ManagedPartitionGroup extends PartitionGroup {

  /**
   * Joins the partition group.
   *
   * @param managementService the partition management service
   * @return a future to be completed once the partition group has been joined
   */
  CompletableFuture<ManagedPartitionGroup> join(PartitionManagementService managementService);

  /**
   * Connects to the partition group.
   *
   * @param managementService the partition management service
   * @return a future to be completed once the partition group has been connected
   */
  CompletableFuture<ManagedPartitionGroup> connect(PartitionManagementService managementService);

  /**
   * Closes the partition group.
   *
   * @return a future to be completed once the partition group has been closed
   */
  CompletableFuture<Void> close();
}
