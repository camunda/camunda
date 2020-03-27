package io.atomix.raft;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RaftFailureListener {

  /**
   * Invoked by {@link io.atomix.raft.partition.RaftPartition} when raft fails.
   *
   * @return a future that should be completed when the actions taken by the listener is completed.
   */
  CompletableFuture<Void> onRaftFailed();
}
