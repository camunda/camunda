package io.atomix.raft.primitive;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Test member. */
public class TestMember implements RaftMember {

  private final MemberId memberId;
  private final Type type;

  public TestMember(final MemberId memberId, final Type type) {
    this.memberId = memberId;
    this.type = type;
  }

  @Override
  public MemberId memberId() {
    return memberId;
  }

  @Override
  public int hash() {
    return 0;
  }

  @Override
  public void addTypeChangeListener(final Consumer<Type> listener) {}

  @Override
  public void removeTypeChangeListener(final Consumer<Type> listener) {}

  @Override
  public CompletableFuture<Void> promote() {
    return null;
  }

  @Override
  public CompletableFuture<Void> promote(final Type type) {
    return null;
  }

  @Override
  public CompletableFuture<Void> demote() {
    return null;
  }

  @Override
  public CompletableFuture<Void> demote(final Type type) {
    return null;
  }

  @Override
  public CompletableFuture<Void> remove() {
    return null;
  }

  @Override
  public Instant getLastUpdated() {
    return null;
  }

  @Override
  public Type getType() {
    return type;
  }
}
