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
package io.atomix.raft;

import io.atomix.cluster.MemberId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** An operation that can be executed on a raft member */
public final class RaftOperation {

  private final BiConsumer<ControllableRaftContexts, MemberId> operation;
  private final String name;

  private RaftOperation(
      final String name, final BiConsumer<ControllableRaftContexts, MemberId> operation) {
    this.name = name;
    this.operation = operation;
  }

  public static RaftOperation of(
      final String name, final BiConsumer<ControllableRaftContexts, MemberId> operation) {
    return new RaftOperation(name, operation);
  }

  public void run(final ControllableRaftContexts raftContext, final MemberId memberId) {
    operation.accept(raftContext, memberId);
  }

  @Override
  public String toString() {
    return name;
  }

  /** Returns a list of RaftOperation */
  public static List<RaftOperation> getRaftOperationsWithSnapshot() {
    final List<RaftOperation> defaultRaftOperation = getDefaultRaftOperations();
    defaultRaftOperation.add(
        RaftOperation.of("Take snapshot", ControllableRaftContexts::snapshotAndCompact));
    return defaultRaftOperation;
  }

  public static List<RaftOperation> getRaftOperationsWithRestarts() {
    final List<RaftOperation> defaultRaftOperation = getDefaultRaftOperations();
    defaultRaftOperation.add(RaftOperation.of("Restart member", ControllableRaftContexts::restart));
    return defaultRaftOperation;
  }

  public static List<RaftOperation> getRaftOperationsWithSnapshotsAndRestarts() {
    final List<RaftOperation> operationsWithSnapshot = getRaftOperationsWithSnapshot();
    operationsWithSnapshot.add(
        RaftOperation.of("Restart member", ControllableRaftContexts::restart));
    return operationsWithSnapshot;
  }

  public static List<RaftOperation> getRaftOperationsWithSnapshotsAndRestartsWithDataLoss() {
    final List<RaftOperation> operations = getRaftOperationsWithSnapshotsAndRestarts();
    operations.add(
        RaftOperation.of(
            "Restart member with full data loss", ControllableRaftContexts::restartWithDataLoss));
    return operations;
  }

  public static List<RaftOperation> getDefaultRaftOperations() {
    final List<RaftOperation> defaultRaftOperation = new ArrayList<>();
    defaultRaftOperation.add(
        RaftOperation.of("Run next task", ControllableRaftContexts::runNextTask));
    defaultRaftOperation.add(
        RaftOperation.of("Receive next message", ControllableRaftContexts::processNextMessage));
    defaultRaftOperation.add(
        RaftOperation.of("Tick electionTimeout", ControllableRaftContexts::tickElectionTimeout));
    defaultRaftOperation.add(
        RaftOperation.of("Tick heartbeatTimeout", ControllableRaftContexts::tickHeartbeatTimeout));
    defaultRaftOperation.add(
        RaftOperation.of(
            "Tick 50ms", (raftContexts, m) -> raftContexts.tick(m, Duration.ofMillis(50))));
    defaultRaftOperation.add(
        RaftOperation.of(
            "Append on leader", (raftContexts, m) -> raftContexts.clientAppendOnLeader()));
    defaultRaftOperation.add(
        RaftOperation.of(
            "Drop next message",
            (raftContexts, m) -> raftContexts.getServerProtocol(m).dropNextMessage()));
    return defaultRaftOperation;
  }
}
