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

import io.atomix.utils.event.ListenerService;

/**
 * Partition primary election service.
 *
 * <p>The primary election service is used to elect primaries and backups for primary-backup
 * replication protocols. Each partition is provided a distinct {@link PrimaryElection} through
 * which it elects a primary.
 */
public interface PrimaryElectionService
    extends ListenerService<PrimaryElectionEvent, PrimaryElectionEventListener> {

  /**
   * Returns the primary election for the given partition identifier.
   *
   * @param partitionId the partition identifier for which to return the primary election
   * @return the primary election for the given partition identifier
   */
  PrimaryElection getElectionFor(PartitionId partitionId);
}
