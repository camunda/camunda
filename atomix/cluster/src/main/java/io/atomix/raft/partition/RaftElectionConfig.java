/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.partition;

import io.atomix.cluster.MemberId;

public final class RaftElectionConfig {

  private final boolean priorityElectionEnabled;
  private final int initialTargetPriority;
  private int nodePriority;
  private MemberId primary;

  private RaftElectionConfig(
      final MemberId primary,
      final boolean priorityElectionEnabled,
      final int initialTargetPriority,
      final int nodePriority) {
    this.primary = primary;
    this.priorityElectionEnabled = priorityElectionEnabled;
    this.initialTargetPriority = initialTargetPriority;
    this.nodePriority = nodePriority;
  }

  public static RaftElectionConfig ofPriorityElection(
      final MemberId primary, final int initialTargetPriority, final int nodePriority) {
    return new RaftElectionConfig(primary, true, initialTargetPriority, nodePriority);
  }

  public static RaftElectionConfig ofDefaultElection() {
    return new RaftElectionConfig(null, false, -1, -1);
  }

  public boolean isPriorityElectionEnabled() {
    return priorityElectionEnabled;
  }

  public int getInitialTargetPriority() {
    return initialTargetPriority;
  }

  public int getNodePriority() {
    return nodePriority;
  }

  public void setNodePriority(final int nodePriority) {
    this.nodePriority = nodePriority;
  }

  public MemberId getPrimary() {
    return primary;
  }

  public void setPrimary(final MemberId primary) {
    this.primary = primary;
  }
}
