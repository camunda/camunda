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
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.utils.concurrent.Scheduled;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
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
    hash = Hashing.murmur3_32().hashUnencodedChars(id.id()).asInt();
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
  public void removeTypeChangeListener(final Consumer<Type> listener) {
    typeChangeListeners.remove(listener);
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
    if (type == this.type) {
      return CompletableFuture.completedFuture(null);
    }
    final CompletableFuture<Void> future = new CompletableFuture<>();
    cluster.getContext().getThreadContext().execute(() -> configure(type, future));
    return future;
  }

  /** Recursively reconfigures the cluster. */
  private void configure(final RaftMember.Type type, final CompletableFuture<Void> future) {
    // Set a timer to retry the attempt to leave the cluster.
    configureTimeout =
        cluster
            .getContext()
            .getThreadContext()
            .schedule(
                cluster.getContext().getElectionTimeout(),
                () -> {
                  configure(type, future);
                });

    // Attempt to leave the cluster by submitting a LeaveRequest directly to the server state.
    // Non-leader states should forward the request to the leader if there is one. Leader states
    // will log, replicate, and commit the reconfiguration.
    cluster
        .getContext()
        .getRaftRole()
        .onReconfigure(
            ReconfigureRequest.builder()
                .withIndex(cluster.getConfiguration().index())
                .withTerm(cluster.getConfiguration().term())
                .withMember(new DefaultRaftMember(id, type, updated))
                .build())
        .whenComplete(
            (response, error) -> {
              if (error == null) {
                if (response.status() == RaftResponse.Status.OK) {
                  cancelConfigureTimer();
                  cluster.configure(
                      new Configuration(
                          response.index(),
                          response.term(),
                          response.timestamp(),
                          response.members()));
                  future.complete(null);
                } else if (response.error() == null
                    || response.error().type() == RaftError.Type.UNAVAILABLE
                    || response.error().type() == RaftError.Type.PROTOCOL_ERROR
                    || response.error().type() == RaftError.Type.NO_LEADER) {
                  cancelConfigureTimer();
                  configureTimeout =
                      cluster
                          .getContext()
                          .getThreadContext()
                          .schedule(
                              cluster.getContext().getElectionTimeout().multipliedBy(2),
                              () -> configure(type, future));
                } else {
                  cancelConfigureTimer();
                  future.completeExceptionally(response.error().createException());
                }
              } else {
                future.completeExceptionally(error);
              }
            });
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
