/*
 * Copyright 2016-present Open Networking Foundation
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
package io.atomix.raft.cluster;

import io.atomix.cluster.MemberId;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents a member of a Raft cluster.
 *
 * <p>This interface provides metadata and operations related to a specific member of a Raft
 * cluster. Each server in a {@link RaftCluster} has a view of the cluster state and can reference
 * and operate on specific members of the cluster via this API.
 */
public interface RaftMember {

  /**
   * Returns the member node ID.
   *
   * @return The member node ID.
   */
  MemberId memberId();

  /**
   * Returns the member hash.
   *
   * @return The member hash.
   */
  int hash();

  /**
   * Adds a listener to be called when the member's type changes.
   *
   * <p>The type change callback will be called when the local server receives notification of the
   * change in type to this member. Type changes may occur at different times from the perspective
   * of different servers but are guaranteed to occur in the same order on all servers.
   *
   * @param listener The listener to be called when the member's type changes.
   */
  void addTypeChangeListener(Consumer<Type> listener);

  /**
   * Removes a type change listener from the member.
   *
   * @param listener The listener to remove from the member.
   */
  void removeTypeChangeListener(Consumer<Type> listener);

  /**
   * Promotes the member to the next highest type.
   *
   * <p>If the member is promoted to {@link Type#ACTIVE} the Raft quorum size will increase.
   *
   * @return A completable future to be completed once the member has been promoted.
   */
  CompletableFuture<Void> promote();

  /**
   * Promotes the member to the given type.
   *
   * <p>If the member is promoted to {@link Type#ACTIVE} the Raft quorum size will increase.
   *
   * @param type The type to which to promote the member.
   * @return A completable future to be completed once the member has been promoted.
   */
  CompletableFuture<Void> promote(RaftMember.Type type);

  /**
   * Demotes the member to the next lowest type.
   *
   * <p>If the member is an {@link Type#ACTIVE} member then demoting it will impact the Raft quorum
   * size.
   *
   * @return A completable future to be completed once the member has been demoted.
   */
  CompletableFuture<Void> demote();

  /**
   * Demotes the member to the given type.
   *
   * <p>If the member is an {@link Type#ACTIVE} member then demoting it will impact the Raft quorum
   * size.
   *
   * @param type The type to which to demote the member.
   * @return A completable future to be completed once the member has been demoted.
   */
  CompletableFuture<Void> demote(RaftMember.Type type);

  /**
   * Removes the member from the configuration.
   *
   * <p>If the member is a part of the current Raft quorum (is an {@link Type#ACTIVE} member) then
   * the quorum will be impacted by removing the member.
   *
   * @return A completable future to be completed once the member has been removed from the
   *     configuration.
   */
  CompletableFuture<Void> remove();

  /**
   * Returns the time at which the member was updated.
   *
   * <p>The member update time is not guaranteed to be consistent across servers or consistent
   * across server restarts. The update time is guaranteed to be monotonically increasing.
   *
   * @return The time at which the member was updated.
   */
  Instant getLastUpdated();

  /**
   * Returns the member type.
   *
   * <p>The member type is indicative of the member's level of participation in the Raft consensus
   * algorithm and asynchronous replication within the cluster. Member types may change throughout
   * the lifetime of the cluster. Types can be changed by {@link #promote(Type) promoting} or {@link
   * #demote(Type) demoting} the member. Member types for a given member are guaranteed to change in
   * the same order on all nodes, but the type of a member may be different from the perspective of
   * different nodes at any given time.
   *
   * @return The member type.
   */
  Type getType();

  /**
   * Indicates how the member participates in voting and replication.
   *
   * <p>The member type defines how a member interacts with the other members of the cluster and,
   * more importantly, how the cluster {@link RaftCluster#getLeader() leader} interacts with the
   * member server. Members can be {@link #promote() promoted} and {@link #demote() demoted} to
   * alter member states. See the specific member types for descriptions of their implications on
   * the cluster.
   */
  enum Type {

    /**
     * Represents an inactive member.
     *
     * <p>The {@code INACTIVE} member type represents a member which does not participate in any
     * communication and is not an active member of the cluster. This is typically the state of a
     * member prior to joining or after leaving a cluster.
     */
    INACTIVE,

    /**
     * Represents a member which participates in asynchronous replication but does not vote in
     * elections or otherwise participate in the Raft consensus algorithm.
     *
     * <p>The {@code PASSIVE} member type is representative of a member that receives state changes
     * from follower nodes asynchronously. As state changes are committed via the {@link #ACTIVE}
     * Raft nodes, committed state changes are asynchronously replicated by followers to passive
     * members. This allows passive members to maintain nearly up-to-date state with minimal impact
     * on the performance of the Raft algorithm itself, and allows passive members to be quickly
     * promoted to {@link #ACTIVE} voting members if necessary.
     */
    PASSIVE,

    /**
     * Represents a non-voting member being caught up to the leader for promotion.
     *
     * <p>This state is used to replicate committed and uncommitted entries to a node in the process
     * of being promoted to {@link #ACTIVE}. It allows a node to be caught up to the leader prior to
     * becoming a voting member to avoid blocking the cluster.
     */
    PROMOTABLE,

    /**
     * Represents a full voting member of the Raft cluster which participates fully in leader
     * election and replication algorithms.
     *
     * <p>The {@code ACTIVE} member type represents a full voting member of the Raft cluster. Active
     * members participate in the Raft leader election and replication algorithms and can themselves
     * be elected leaders.
     */
    ACTIVE,
  }
}
