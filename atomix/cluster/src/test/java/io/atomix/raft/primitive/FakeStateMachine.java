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
package io.atomix.raft.primitive;

import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.impl.RaftServiceManager;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import java.time.Duration;

public class FakeStateMachine extends RaftServiceManager {

  public FakeStateMachine(
      final RaftContext context,
      final ThreadContext threadContext,
      final ThreadContextFactory threadContextFactory) {
    super(context, threadContext, threadContextFactory);
  }

  @Override
  protected Duration getCompactDelay() {
    return Duration.ZERO;
  }

  @Override
  protected Duration getSnapshotCompletionDelay() {
    return Duration.ZERO;
  }

  @Override
  protected Duration getSnapshotInterval() {
    return Duration.ofMillis(10);
  }
}
