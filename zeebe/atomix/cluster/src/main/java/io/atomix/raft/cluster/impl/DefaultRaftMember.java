/*
 * Copyright 2015-present Open Networking Foundation
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
 * limitations under the License
 */
package io.atomix.raft.cluster.impl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.hash.Hashing;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftException;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.utils.concurrent.Scheduled;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Cluster member. */
public final class DefaultRaftMember implements RaftMember, AutoCloseable {

  private final MemberId id;
  private final int hash;
  private final transient Set<Consumer<Type>> typeChangeListeners = new CopyOnWriteArraySet<>();
  private Type type;
  private Instant updated;
  private transient Scheduled configureTimeout;
  private transient RaftClusterContext cluster;

  public DefaultRaftMember(final MemberId id, final Type type, final Instant updated) {
    this.id = checkNotNull(id, "id cannot be null");
    hash = Hashing.murmur3_32_fixed().hashUnencodedChars(id.id()).asInt();
    setType(checkNotNull(type, "type cannot be null"));
    this.updated = checkNotNull(updated, "updated cannot be null");
  }

  @Override
  public MemberId memberId() {
    return id;
  }

  @Override
  public int hash() {
    return hash;
  }

  @Override
  public void addTypeChangeListener(final Consumer<Type> listener) {
    typeChangeListeners.add(listener);
  }

  @Override
  public CompletableFuture<Void> promote() {
    if (Type.values().length > type.ordinal() + 1) {
      return configure(Type.values()[type.ordinal() + 1]);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> promote(final Type type) {
    return configure(type);
  }

  @Override
  public CompletableFuture<Void> demote() {
    if (type.ordinal() > 0) {
      return configure(Type.values()[type.ordinal() - 1]);
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> demote(final Type type) {
    return configure(type);
  }

  @Override
  public CompletableFuture<Void> remove() {
    return configure(Type.INACTIVE);
  }

  @Override
  public Instant getLastUpdated() {
    return updated;
  }

  @Override
  public RaftMember.Type getType() {
    return type;
  }

  /**
   * Sets the member type.
   *
   * @param type the member type
   */
  void setType(final Type type) {
    this.type = type;
  }

  /**
   * Updates the member type.
   *
   * @param type The member type.
   * @return The member.
   */
  public DefaultRaftMember update(final RaftMember.Type type, final Instant time) {
    if (this.type != type) {
      setType(checkNotNull(type, "type cannot be null"));
      if (time.isAfter(updated)) {
        updated = checkNotNull(time, "time cannot be null");
      }
      typeChangeListeners.forEach(l -> l.accept(type));
    }
    return this;
  }

  /** Demotes the server to the given type. */
  private CompletableFuture<Void> configure(final RaftMember.Type type) {
    // Deliberately no short-circuit when the requested type equals the locally known one: this
    // member's view of its own type can be stale or outright wrong - a member that restarted
    // without a stored configuration falls back to the fabricated all-ACTIVE initial configuration
    // - and answering from it would report a promotion or demotion as successful without anything
    // ever reaching the leader. The leader is the authority: a request that does not change its
    // configuration is answered OK by the equal-membership branch of LeaderRole#onReconfigure, and
    // one built from a stale view is rejected as a stale configuration for the caller to retry.
    final CompletableFuture<Void> future = new CompletableFuture<>();
    // The retry timer is owned by this operation, not by the member: a stale response for an
    // earlier, already completed operation must never cancel a newer operation's timer - the only
    // thing that would drive that operation forward - and would otherwise hang its future
    // forever. At the same time there is at most one live timer per operation: every reschedule
    // replaces (and cancels) the previous one, so duplicated in-flight attempts of the same
    // operation cannot multiply timers.
    final var retryTimer = new AtomicReference<Scheduled>();
    cluster.getContext().getThreadContext().execute(() -> configure(type, future, retryTimer));
    return future;
  }

  /** Recursively reconfigures the cluster. */
  private void configure(
      final RaftMember.Type type,
      final CompletableFuture<Void> future,
      final AtomicReference<Scheduled> retryTimer) {
    if (future.isDone()) {
      // The operation already completed, e.g. the retry timer fired while the response that
      // completed the future was still in flight. Don't send another request or re-arm the timer.
      return;
    }
    final var currentConfiguration = cluster.getConfiguration();
    if (currentConfiguration == null) {
      // The local member does not know a configuration yet, for example a PROMOTABLE member whose
      // join completed before the leader disseminated the configuration to it. The request is
      // built from the local configuration view, so fail fast - like a stale-view
      // CONFIGURATION_ERROR - and let the caller retry once a configuration is known.
      cancelRetryTimer(retryTimer);
      future.completeExceptionally(
          new RaftException.ConfigurationException(
              null, "Cannot change type of member %s to %s: no known configuration", id, type));
      return;
    }

    // Set a timer to retry the attempt in case the response is lost.
    scheduleRetry(cluster.getContext().getElectionTimeout(), type, future, retryTimer);

    // Attempt to reconfigure by submitting a ReconfigureRequest directly to the server state.
    // Non-leader states should forward the request to the leader if there is one. Leader states
    // will log, replicate, and commit the reconfiguration.
    cluster
        .getContext()
        .getRaftRole()
        .onReconfigure(
            ReconfigureRequest.builder()
                .withIndex(currentConfiguration.index())
                .withTerm(cluster.getConfigurationTerm())
                .withMembers(currentConfiguration.newMembers())
                // Override local member with the new type.
                .withMember(new DefaultRaftMember(id, type, updated))
                .from(cluster.getLocalMember().memberId().id())
                .build())
        .whenComplete(
            (response, error) -> {
              if (future.isDone()) {
                // A late response for an already completed operation must not touch any timer:
                // the caller may have observed the completion and started a new operation, whose
                // timer must stay armed.
                return;
              }
              if (error == null) {
                if (response.status() == RaftResponse.Status.OK) {
                  cancelRetryTimer(retryTimer);
                  // Complete the future even if applying the new configuration fails: the
                  // exception would otherwise be swallowed by this callback and, with the retry
                  // timer already cancelled, hang the future forever.
                  try {
                    cluster.configure(
                        new Configuration(
                            response.index(),
                            response.term(),
                            response.timestamp(),
                            response.members()));
                    future.complete(null);
                  } catch (final Exception e) {
                    future.completeExceptionally(e);
                  }
                } else if (response.error() == null
                    || response.error().type() == RaftError.Type.UNAVAILABLE
                    || response.error().type() == RaftError.Type.PROTOCOL_ERROR
                    || response.error().type() == RaftError.Type.NO_LEADER) {
                  scheduleRetry(
                      cluster.getContext().getElectionTimeout().multipliedBy(2),
                      type,
                      future,
                      retryTimer);
                } else {
                  cancelRetryTimer(retryTimer);
                  future.completeExceptionally(response.error().createException());
                }
              } else {
                cancelRetryTimer(retryTimer);
                future.completeExceptionally(error);
              }
            });
  }

  /**
   * Replaces the operation's retry timer with a new one, so that at most one timer is live per
   * operation. The member-level configureTimeout field tracks the newest timer solely so that
   * {@link #close()} can cancel it.
   */
  private void scheduleRetry(
      final Duration delay,
      final RaftMember.Type type,
      final CompletableFuture<Void> future,
      final AtomicReference<Scheduled> retryTimer) {
    final var timer =
        cluster
            .getContext()
            .getThreadContext()
            .schedule(delay, () -> configure(type, future, retryTimer));
    final var previous = retryTimer.getAndSet(timer);
    if (previous != null) {
      previous.cancel();
    }
    configureTimeout = timer;
  }

  private void cancelRetryTimer(final AtomicReference<Scheduled> retryTimer) {
    final var timer = retryTimer.getAndSet(null);
    if (timer != null) {
      timer.cancel();
    }
  }

  @Override
  public void close() {
    cancelConfigureTimer();
  }

  /** Cancels the configure timeout. */
  private void cancelConfigureTimer() {
    if (configureTimeout != null) {
      configureTimeout.cancel();
      configureTimeout = null;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), id);
  }

  @Override
  public boolean equals(final Object object) {
    return object instanceof DefaultRaftMember && ((DefaultRaftMember) object).id.equals(id);
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("id", id).add("type", type).add("updated", updated).toString();
  }

  /** Sets the member's parent cluster. */
  DefaultRaftMember setCluster(final RaftClusterContext cluster) {
    this.cluster = cluster;
    return this;
  }
}
