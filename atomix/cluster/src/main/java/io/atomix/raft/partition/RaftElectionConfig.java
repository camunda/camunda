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

public final class RaftElectionConfig {

  private final boolean priorityElectionEnabled;
  private final int initialTargetPriority;
  private final int nodePriority;

  private RaftElectionConfig(
      final boolean priorityElectionEnabled,
      final int initialTargetPriority,
      final int nodePriority) {
    this.priorityElectionEnabled = priorityElectionEnabled;
    this.initialTargetPriority = initialTargetPriority;
    this.nodePriority = nodePriority;
  }

  public static RaftElectionConfig ofPriorityElection(
      final int initialTargetPriority, final int nodePriority) {
    return new RaftElectionConfig(true, initialTargetPriority, nodePriority);
  }

  public static RaftElectionConfig ofDefaultElection() {
    return new RaftElectionConfig(false, -1, -1);
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
}
