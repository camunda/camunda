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
import java.util.concurrent.CompletableFuture;

/**
 * Partition primary election.
 *
 * <p>A primary election is used to elect a primary and backups for a single partition. To enter a
 * primary election for a partition, a node must call the {@link #enter(GroupMember)} method. Once
 * an election is complete, the {@link PrimaryTerm} can be read via {@link #getTerm()}.
 *
 * <p>The prioritization of candidates within a primary election is unspecified.
 */
public interface PrimaryElection
    extends ListenerService<PrimaryElectionEvent, PrimaryElectionEventListener> {

  /**
   * Enters the primary election.
   *
   * <p>When entering a primary election, the provided {@link GroupMember} will be added to the
   * election's candidate list. The returned term is representative of the term <em>after</em> the
   * member joins the election. Thus, if the joining member is immediately elected primary, the
   * returned term should reflect that.
   *
   * @param member the member to enter the election
   * @return the current term
   */
  CompletableFuture<PrimaryTerm> enter(GroupMember member);

  /**
   * Returns the current term.
   *
   * <p>The term is representative of the current primary, candidates, and backups in the primary
   * election.
   *
   * @return the current term
   */
  CompletableFuture<PrimaryTerm> getTerm();
}
