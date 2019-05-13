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
package io.zeebe.distributedlog.restore;

import io.atomix.cluster.MemberId;
import java.util.concurrent.CompletableFuture;

public interface PartitionLeaderElectionController {

  /**
   * Withdraws from the leader election, stepping down if it is the leader.
   *
   * @return a future which completes when the node has withdrawn from the election primitive
   */
  CompletableFuture<Void> withdraw();

  /**
   * Rejoins the leader election primitive.
   *
   * @return a future which completes when the node has joined the election primitive
   */
  CompletableFuture<Void> join();

  /** @return the current elected leader, or null if none yet elected */
  MemberId getLeader();
}
