/*
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
package io.atomix.raft;

import io.atomix.cluster.MemberId;
import java.util.Optional;

/**
 * Broker-supplied check that the node asking for a leadership transfer really is the cluster's
 * current coordinator.
 *
 * <p>The coordinator is the lowest-id member of the committed cluster configuration, which is a
 * broker-level concern: it spans every partition, and it is not necessarily the same set as this
 * partition's Raft members.
 */
@FunctionalInterface
public interface LeadershipTransferCoordinatorCheck {
  /**
   * Check for a server with no broker attached (e.g. Raft-only tests): there is no cluster
   * configuration to check a coordinator against, so every requester is taken at its word.
   */
  LeadershipTransferCoordinatorCheck NONE = (coordinator, configurationVersion) -> Optional.empty();

  /**
   * Returns the reason to refuse {@code coordinator}, or empty if it may request transfers.
   *
   * @param coordinator the node that requested the transfer
   * @param configurationVersion the version of the committed cluster configuration the coordinator
   *     based its request on
   */
  Optional<LeadershipTransferResult> validate(MemberId coordinator, long configurationVersion);
}
