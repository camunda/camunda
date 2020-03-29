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
import io.atomix.raft.RaftServer;
import io.atomix.storage.StorageLevel;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Raft server cluster API.
 *
 * <p>This class provides the view of the Raft cluster from the perspective of a single server. When
 * a {@link RaftServer RaftServer} is started, the server will form a cluster with other servers.
 * Each Raft cluster consists of some set of {@link #getMembers() members}, and each {@link
 * RaftMember} represents a single server in the cluster. Users can use the {@code Cluster} to react
 * to state changes in the underlying Raft algorithm via the various listeners.
 *
 * <p>
 *
 * <pre>{@code
 * server.cluster().onJoin(member -> {
 *   System.out.println(member.address() + " joined the cluster!");
 * });
 *
 * }</pre>
 *
 * Membership exposed via this interface is provided from the perspective of the local server and
 * may not necessarily be consistent with cluster membership from the perspective of other nodes.
 * The only consistent membership list is on the {@link #getLeader() leader} node.
 *
 * <h2>Cluster management</h2>
 *
 * Users can use the {@code Cluster} to manage the Raft cluster membership. Typically, servers join
 * the cluster by calling {@link RaftServer#bootstrap(MemberId...)} or {@link #join(MemberId...)},
 * but in the event that a server fails permanently and thus cannot remove itself, other nodes can
 * remove arbitrary servers.
 *
 * <p>
 *
 * <pre>{@code
 * server.cluster().onJoin(member -> {
 *   member.remove().thenRun(() -> System.out.println("Removed " + member.address() + " from the cluster!"));
 * });
 *
 * }</pre>
 *
 * When a member is removed from the cluster, the configuration change removing the member will be
 * replicated to all the servers in the cluster and persisted to disk. Once a member has been
 * removed, for that member to rejoin the cluster it must fully restart and request to rejoin the
 * cluster. For servers configured with a persistent {@link StorageLevel}, cluster configurations
 * are stored on disk.
 *
 * <p>Additionally, members can be {@link RaftMember#promote() promoted} and {@link
 * RaftMember#demote() demoted} by any other member of the cluster. When a member state is changed,
 * a cluster configuration change request is sent to the cluster leader where it's logged and
 * replicated through the Raft consensus algorithm. <em>During</em> the configuration change,
 * servers' cluster configurations will be updated. Once the configuration change is complete, it
 * will be persisted to disk on all servers and is guaranteed not to be lost even in the event of a
 * full cluster shutdown (assuming the server uses a persistent {@link StorageLevel}).
 */
public interface RaftCluster {

  /**
   * Adds a listener to be called when a leader is elected.
   *
   * <p>The provided {@code callback} will be called when a new leader is elected for a term.
   * Because Raft ensures only a single leader can be elected for any given term, each election
   * callback will be called at most once per term. However, note that a leader may not be elected
   * for a term as well.
   *
   * <pre>{@code
   * server.cluster().onLeaderElection(member -> {
   *   System.out.println(member.address() + " elected for term " + server.cluster().term());
   * });
   *
   * }</pre>
   *
   * The {@link RaftMember} provided to the callback represents the member that was elected leader.
   * Raft guarantees that this member is a member of the {@link RaftCluster}. When a leader election
   * callback is called, the correct {@link #getTerm()} for the leader is guaranteed to have already
   * been set. Thus, to get the term for the provided leader, simply read the cluster {@link
   * #getTerm()}.
   *
   * @param listener The listener to be called when a new leader is elected.
   */
  void addLeaderElectionListener(Consumer<RaftMember> listener);

  /**
   * Removes a leader election listener from the cluster.
   *
   * @param listener The leader election listener to remove.
   */
  void removeLeaderElectionListener(Consumer<RaftMember> listener);

  /**
   * Returns a member by ID.
   *
   * <p>The returned {@link RaftMember} is referenced by the unique {@link RaftMember#memberId()}.
   *
   * @param id The member ID.
   * @return The member or {@code null} if no member with the given {@code id} exists.
   */
  RaftMember getMember(MemberId id);

  /**
   * Bootstraps the cluster.
   *
   * <p>Bootstrapping the cluster results in a new cluster being formed with the provided
   * configuration. The initial nodes in a cluster must always be bootstrapped. This is necessary to
   * prevent split brain. If the provided configuration is empty, the local server will form a
   * single-node cluster.
   *
   * <p>Only {@link RaftMember.Type#ACTIVE} members can be included in a bootstrap configuration. If
   * the local server is not initialized as an active member, it cannot be part of the bootstrap
   * configuration for the cluster.
   *
   * <p>When the cluster is bootstrapped, the local server will be transitioned into the active
   * state and begin participating in the Raft consensus algorithm. When the cluster is first
   * bootstrapped, no leader will exist. The bootstrapped members will elect a leader amongst
   * themselves. Once a cluster has been bootstrapped, additional members may be {@link
   * #join(MemberId...) joined} to the cluster. In the event that the bootstrapped members cannot
   * reach a quorum to elect a leader, bootstrap will continue until successful.
   *
   * <p>It is critical that all servers in a bootstrap configuration be started with the same exact
   * set of members. Bootstrapping multiple servers with different configurations may result in
   * split brain.
   *
   * <p>The {@link CompletableFuture} returned by this method will be completed once the cluster has
   * been bootstrapped, a leader has been elected, and the leader has been notified of the local
   * server's client configurations.
   *
   * @param cluster The bootstrap cluster configuration.
   * @return A completable future to be completed once the cluster has been bootstrapped.
   */
  default CompletableFuture<Void> bootstrap(final MemberId... cluster) {
    return bootstrap(Arrays.asList(cluster));
  }

  /**
   * Bootstraps the cluster.
   *
   * <p>Bootstrapping the cluster results in a new cluster being formed with the provided
   * configuration. The initial nodes in a cluster must always be bootstrapped. This is necessary to
   * prevent split brain. If the provided configuration is empty, the local server will form a
   * single-node cluster.
   *
   * <p>Only {@link RaftMember.Type#ACTIVE} members can be included in a bootstrap configuration. If
   * the local server is not initialized as an active member, it cannot be part of the bootstrap
   * configuration for the cluster.
   *
   * <p>When the cluster is bootstrapped, the local server will be transitioned into the active
   * state and begin participating in the Raft consensus algorithm. When the cluster is first
   * bootstrapped, no leader will exist. The bootstrapped members will elect a leader amongst
   * themselves. Once a cluster has been bootstrapped, additional members may be {@link
   * #join(MemberId...) joined} to the cluster. In the event that the bootstrapped members cannot
   * reach a quorum to elect a leader, bootstrap will continue until successful.
   *
   * <p>It is critical that all servers in a bootstrap configuration be started with the same exact
   * set of members. Bootstrapping multiple servers with different configurations may result in
   * split brain.
   *
   * <p>The {@link CompletableFuture} returned by this method will be completed once the cluster has
   * been bootstrapped, a leader has been elected, and the leader has been notified of the local
   * server's client configurations.
   *
   * @param cluster The bootstrap cluster configuration.
   * @return A completable future to be completed once the cluster has been bootstrapped.
   */
  CompletableFuture<Void> bootstrap(Collection<MemberId> cluster);

  /**
   * Joins the cluster as a listener.
   *
   * <p>Joining the cluster results in the local server being added to an existing cluster that has
   * already been bootstrapped. The provided configuration will be used to connect to the existing
   * cluster and submit a join request. Once the server has been added to the existing cluster's
   * configuration, the join operation is complete.
   *
   * <p>Any {@link RaftMember.Type type} of server may join a cluster. In order to join a cluster,
   * the provided list of bootstrapped members must be non-empty and must include at least one
   * active member of the cluster. If no member in the configuration is reachable, the server will
   * continue to attempt to join the cluster until successful. If the provided cluster configuration
   * is empty, the returned {@link CompletableFuture} will be completed exceptionally.
   *
   * <p>When the server joins the cluster, the local server will be transitioned into its initial
   * state as defined by the configured {@link RaftMember.Type}. Once the server has joined, it will
   * immediately begin participating in Raft and asynchronous replication according to its
   * configuration.
   *
   * <p>It's important to note that the provided cluster configuration will only be used the first
   * time the server attempts to join the cluster. Thereafter, in the event that the server crashes
   * and is restarted by {@code join}ing the cluster again, the last known configuration will be
   * used assuming the server is configured with persistent storage. Only when the server leaves the
   * cluster will its configuration and log be reset.
   *
   * <p>In order to preserve safety during configuration changes, Raft leaders do not allow
   * concurrent configuration changes. In the event that an existing configuration change (a server
   * joining or leaving the cluster or a member being {@link RaftMember#promote() promoted} or
   * {@link RaftMember#demote() demoted}) is under way, the local server will retry attempts to join
   * the cluster until successful. If the server fails to reach the leader, the join will be retried
   * until successful.
   *
   * @param cluster A list of cluster member addresses to join.
   * @return A completable future to be completed once the local server has joined the cluster.
   */
  default CompletableFuture<Void> listen(final MemberId... cluster) {
    return join(Arrays.asList(cluster));
  }

  /**
   * Joins the cluster.
   *
   * <p>Joining the cluster results in the local server being added to an existing cluster that has
   * already been bootstrapped. The provided configuration will be used to connect to the existing
   * cluster and submit a join request. Once the server has been added to the existing cluster's
   * configuration, the join operation is complete.
   *
   * <p>Any {@link RaftMember.Type type} of server may join a cluster. In order to join a cluster,
   * the provided list of bootstrapped members must be non-empty and must include at least one
   * active member of the cluster. If no member in the configuration is reachable, the server will
   * continue to attempt to join the cluster until successful. If the provided cluster configuration
   * is empty, the returned {@link CompletableFuture} will be completed exceptionally.
   *
   * <p>When the server joins the cluster, the local server will be transitioned into its initial
   * state as defined by the configured {@link RaftMember.Type}. Once the server has joined, it will
   * immediately begin participating in Raft and asynchronous replication according to its
   * configuration.
   *
   * <p>It's important to note that the provided cluster configuration will only be used the first
   * time the server attempts to join the cluster. Thereafter, in the event that the server crashes
   * and is restarted by {@code join}ing the cluster again, the last known configuration will be
   * used assuming the server is configured with persistent storage. Only when the server leaves the
   * cluster will its configuration and log be reset.
   *
   * <p>In order to preserve safety during configuration changes, Raft leaders do not allow
   * concurrent configuration changes. In the event that an existing configuration change (a server
   * joining or leaving the cluster or a member being {@link RaftMember#promote() promoted} or
   * {@link RaftMember#demote() demoted}) is under way, the local server will retry attempts to join
   * the cluster until successful. If the server fails to reach the leader, the join will be retried
   * until successful.
   *
   * @param cluster A collection of cluster member addresses to join.
   * @return A completable future to be completed once the local server has joined the cluster.
   */
  CompletableFuture<Void> join(Collection<MemberId> cluster);

  /**
   * Joins the cluster as a listener.
   *
   * <p>Joining the cluster results in the local server being added to an existing cluster that has
   * already been bootstrapped. The provided configuration will be used to connect to the existing
   * cluster and submit a join request. Once the server has been added to the existing cluster's
   * configuration, the join operation is complete.
   *
   * <p>Any {@link RaftMember.Type type} of server may join a cluster. In order to join a cluster,
   * the provided list of bootstrapped members must be non-empty and must include at least one
   * active member of the cluster. If no member in the configuration is reachable, the server will
   * continue to attempt to join the cluster until successful. If the provided cluster configuration
   * is empty, the returned {@link CompletableFuture} will be completed exceptionally.
   *
   * <p>When the server joins the cluster, the local server will be transitioned into its initial
   * state as defined by the configured {@link RaftMember.Type}. Once the server has joined, it will
   * immediately begin participating in Raft and asynchronous replication according to its
   * configuration.
   *
   * <p>It's important to note that the provided cluster configuration will only be used the first
   * time the server attempts to join the cluster. Thereafter, in the event that the server crashes
   * and is restarted by {@code join}ing the cluster again, the last known configuration will be
   * used assuming the server is configured with persistent storage. Only when the server leaves the
   * cluster will its configuration and log be reset.
   *
   * <p>In order to preserve safety during configuration changes, Raft leaders do not allow
   * concurrent configuration changes. In the event that an existing configuration change (a server
   * joining or leaving the cluster or a member being {@link RaftMember#promote() promoted} or
   * {@link RaftMember#demote() demoted}) is under way, the local server will retry attempts to join
   * the cluster until successful. If the server fails to reach the leader, the join will be retried
   * until successful.
   *
   * @param cluster A collection of cluster member addresses to join.
   * @return A completable future to be completed once the local server has joined the cluster.
   */
  CompletableFuture<Void> listen(Collection<MemberId> cluster);

  /**
   * Joins the cluster.
   *
   * <p>Joining the cluster results in the local server being added to an existing cluster that has
   * already been bootstrapped. The provided configuration will be used to connect to the existing
   * cluster and submit a join request. Once the server has been added to the existing cluster's
   * configuration, the join operation is complete.
   *
   * <p>Any {@link RaftMember.Type type} of server may join a cluster. In order to join a cluster,
   * the provided list of bootstrapped members must be non-empty and must include at least one
   * active member of the cluster. If no member in the configuration is reachable, the server will
   * continue to attempt to join the cluster until successful. If the provided cluster configuration
   * is empty, the returned {@link CompletableFuture} will be completed exceptionally.
   *
   * <p>When the server joins the cluster, the local server will be transitioned into its initial
   * state as defined by the configured {@link RaftMember.Type}. Once the server has joined, it will
   * immediately begin participating in Raft and asynchronous replication according to its
   * configuration.
   *
   * <p>It's important to note that the provided cluster configuration will only be used the first
   * time the server attempts to join the cluster. Thereafter, in the event that the server crashes
   * and is restarted by {@code join}ing the cluster again, the last known configuration will be
   * used assuming the server is configured with persistent storage. Only when the server leaves the
   * cluster will its configuration and log be reset.
   *
   * <p>In order to preserve safety during configuration changes, Raft leaders do not allow
   * concurrent configuration changes. In the event that an existing configuration change (a server
   * joining or leaving the cluster or a member being {@link RaftMember#promote() promoted} or
   * {@link RaftMember#demote() demoted}) is under way, the local server will retry attempts to join
   * the cluster until successful. If the server fails to reach the leader, the join will be retried
   * until successful.
   *
   * @param cluster A list of cluster member addresses to join.
   * @return A completable future to be completed once the local server has joined the cluster.
   */
  default CompletableFuture<Void> join(final MemberId... cluster) {
    return join(Arrays.asList(cluster));
  }

  /**
   * Leaves the cluster.
   *
   * <p>Invocations of this method will cause the local {@link RaftServer} to leave the cluster.
   * <em>This method is for advanced usage only.</em> Typically, users should use {@link
   * RaftServer#leave()} to leave the cluster and close a server in order to ensure all associated
   * resources are properly closed.
   *
   * <p>When a server leaves the cluster, the server submits a {@link
   * io.atomix.raft.protocol.LeaveRequest} to the cluster leader. The leader will replicate and
   * commit the configuration change in order to remove the leaving server from the cluster and
   * notify each member of the leaving server.
   *
   * <p>In order to preserve safety during configuration changes, Raft leaders do not allow
   * concurrent configuration changes. In the event that an existing configuration change (a server
   * joining or leaving the cluster or a member being {@link RaftMember#promote() promoted} or
   * {@link RaftMember#demote() demoted}) is under way, the local server will retry attempts to
   * leave the cluster until successful. The server will continuously attempt to leave the cluster
   * until successful.
   *
   * @return A completable future to be completed once the local server has left the cluster.
   */
  CompletableFuture<Void> leave();

  /**
   * Registers a callback to be called when a member leaves the cluster.
   *
   * <p>The registered {@code callback} will be called whenever an existing {@link RaftMember}
   * leaves the cluster. Membership changes are sequentially consistent, meaning each server in the
   * cluster will see members leave in the same order, but different servers may see members leave
   * at different points in time. Users should not in any case assume that because one server has
   * seen a member leave the cluster all servers have.
   *
   * @param listener The listener to be called when a member leaves the cluster.
   */
  void addListener(RaftClusterEventListener listener);

  /**
   * Removes a listener from the cluster.
   *
   * @param listener The listener to remove from the cluster.
   */
  void removeListener(RaftClusterEventListener listener);

  /**
   * Returns the current cluster leader.
   *
   * <p>If no leader has been elected for the current {@link #getTerm() term}, the leader will be
   * {@code null}. Once a leader is elected, the leader must be known to the local server's
   * configuration. If the returned {@link RaftMember} is {@code null} then that does not
   * necessarily indicate that no leader yet exists for the current term, only that the local server
   * has not learned of a valid leader for the term.
   *
   * @return The current cluster leader or {@code null} if no leader is known for the current term.
   */
  RaftMember getLeader();

  /**
   * Returns the local cluster member.
   *
   * @return The local cluster member.
   */
  RaftMember getMember();

  /**
   * Returns a collection of all cluster members.
   *
   * <p>The returned members are representative of the last configuration known to the local server.
   * Over time, the cluster configuration may change. In the event of a membership change, the
   * returned {@link Collection} will not be modified, but instead a new collection will be created.
   * Similarly, modifying the returned collection will have no impact on the cluster membership.
   *
   * @return A collection of all cluster members.
   */
  Collection<RaftMember> getMembers();

  /**
   * Returns the current cluster term.
   *
   * <p>The term is representative of the epoch determined by the underlying Raft consensus
   * algorithm. The term is a monotonically increasing number used by Raft to represent a point in
   * logical time. If the cluster is persistent (i.e. all servers use a persistent {@link
   * StorageLevel}), the term is guaranteed to be unique and monotonically increasing even across
   * cluster restarts. Additionally, for any given term, Raft guarantees that only a single {@link
   * #getLeader() leader} can be elected.
   *
   * @return The current cluster term.
   */
  long getTerm();
}
