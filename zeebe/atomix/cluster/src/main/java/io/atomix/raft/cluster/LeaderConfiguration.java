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
package io.atomix.raft.cluster;

import io.atomix.cluster.MemberId;
import io.atomix.raft.storage.system.Configuration;

/**
 * The configuration of the leader, see {@link io.atomix.raft.RaftServer#readLeaderConfiguration()}.
 *
 * @param leader the leader that answered
 * @param configuration its current configuration, with the configuration term
 * @param committed true if the leader has committed the configuration
 */
public record LeaderConfiguration(MemberId leader, Configuration configuration, boolean committed) {

  /**
   * @return true if the configuration is committed and no joint change is in progress
   */
  public boolean isCommittedAndNotJoint() {
    return committed && !configuration.requiresJointConsensus();
  }
}
