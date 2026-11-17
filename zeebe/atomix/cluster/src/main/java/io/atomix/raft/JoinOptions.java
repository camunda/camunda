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

/**
 * Options of a join, see {@link RaftServer#join(io.atomix.raft.cluster.RaftMember.Type,
 * java.util.Collection, JoinOptions)}.
 *
 * @param incarnation the incarnation of the local member. A member that starts without data uses a
 *     new incarnation, so that a fence entry of its previous incarnation does not apply to it.
 * @param configurationVersion an opaque version that the leader passes to its {@link
 *     JoinAdmission}, for example the version of the joiner's view of the cluster membership; 0 if
 *     unknown
 */
public record JoinOptions(long incarnation, long configurationVersion) {

  public static JoinOptions withIncarnation(final long incarnation) {
    return new JoinOptions(incarnation, 0);
  }

  public JoinOptions withConfigurationVersion(final long configurationVersion) {
    return new JoinOptions(incarnation, configurationVersion);
  }
}
