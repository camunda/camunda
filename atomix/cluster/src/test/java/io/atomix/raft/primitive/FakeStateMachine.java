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
