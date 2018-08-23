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
package io.zeebe.raft;

import io.zeebe.raft.state.RaftState;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Collection;

public interface RaftStateListener {
  /**
   * Notifies on raft state change.
   *
   * @param raft the current raft node
   * @param raftState the new state of the current raft
   */
  default void onStateChange(Raft raft, RaftState raftState) {}

  /**
   * Notifies that a raft member wants to leave the cluster. <br>
   *
   * <p>The calling implementation can do some async clean up and return an future, which should be
   * completed if his work is done. Raft waits until all futures of his RaftStateListeners are done.
   * After that Raft proceed with his leaving process. <br>
   *
   * <p>Note: this callback is only called if this Raft Node is the leader for the corresponding
   * partition.
   *
   * @param raft the curent leader node
   * @param nodeIds the new member list (without the leaving raft)
   * @return an future on which the leader should waits, before processing the member leave
   */
  default ActorFuture<Void> onMemberLeaving(Raft raft, Collection<Integer> nodeIds) {
    return CompletableActorFuture.completed(null);
  }

  /**
   * Notifies that a raft member joined the cluster. <br>
   *
   * <p>Note: this callback is called on leader and followers
   *
   * @param raft the current raft node
   * @param currentNodeIds the new member node id list (including the new member)
   */
  default void onMemberJoined(Raft raft, Collection<Integer> currentNodeIds) {}
}
