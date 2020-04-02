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
package io.atomix.raft.zeebe.util;

import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.impl.RaftServiceManager;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import java.time.Duration;

public class ZeebeRaftStateMachine extends RaftServiceManager {

  private static final Duration SNAPSHOT_COMPLETION_DELAY = Duration.ofMillis(0);
  private static final Duration COMPACT_DELAY = Duration.ofMillis(0);

  private volatile long compactableIndex;
  private volatile long compactableTerm;

  public ZeebeRaftStateMachine(
      final RaftContext raft,
      final ThreadContext stateContext,
      final ThreadContextFactory threadContextFactory) {
    super(raft, stateContext, threadContextFactory);
  }

  @Override
  public long getCompactableIndex() {
    return compactableIndex;
  }

  @Override
  public void setCompactableIndex(final long index) {
    compactableIndex = index;
  }

  @Override
  public long getCompactableTerm() {
    return compactableTerm;
  }

  @Override
  protected Duration getCompactDelay() {
    return COMPACT_DELAY;
  }

  @Override
  protected Duration getSnapshotCompletionDelay() {
    return SNAPSHOT_COMPLETION_DELAY;
  }
}
