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

/**
 * Decides on the leader whether a member may be added to the configuration: by a join, or as a new
 * member of a full target configuration ({@link RaftServer#reconfigure(java.util.Map, long,
 * long)}). The owner of the server supplies it, because Raft does not know who may be a member. The
 * default admits every member.
 *
 * <p>It is called on the Raft thread and must not block.
 */
@FunctionalInterface
public interface JoinAdmission {

  JoinAdmission ADMIT_ALL = (member, configurationVersion) -> Decision.ADMIT;

  /**
   * @param member the member to add
   * @param configurationVersion the version that the joiner passed in its {@link JoinOptions}, or 0
   *     if unknown
   * @return the decision; a refusal is retriable for the joiner
   */
  Decision check(MemberId member, long configurationVersion);

  enum Decision {
    ADMIT,
    /** The member is not a member of the cluster; it may become one later. */
    NOT_A_MEMBER,
    /** The leader cannot decide yet, for example because it does not know the cluster yet. */
    NOT_READY
  }
}
