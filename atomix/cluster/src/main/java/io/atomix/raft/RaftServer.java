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
 * limitations under the License.
 */
package io.atomix.raft;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.atomix.raft.RaftException.ConfigurationException;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftCluster;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.impl.DefaultRaftServer;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.zeebe.EntryValidator;
import io.atomix.raft.zeebe.NoopEntryValidator;
import io.atomix.storage.StorageLevel;
import io.atomix.storage.journal.index.JournalIndex;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Provides a standalone implementation of the <a href="http://raft.github.io/">Raft consensus
 * algorithm</a>.
 *
 * <p>To create a new server, use the server {@link RaftServer.Builder}. Servers require cluster
 * membership information in order to perform communication. Each server must be provided a local
 * {@link MemberId} to which to bind the internal {@link io.atomix.raft.protocol.RaftServerProtocol}
 * and a set of addresses for other members in the cluster.
 *
 * <h2>State machines</h2>
 *
 * State machines are provided in a factory to allow servers to transition between stateful and
 * stateless states.
 *
 * <pre>{@code
 * Address address = new Address("123.456.789.0", 5000);
 * Collection<Address> members = Arrays.asList(new Address("123.456.789.1", 5000), new Address("123.456.789.2", 5000));
 *
 * RaftServer server = RaftServer.builder(address)
 *   .withStateMachine(MyStateMachine::new)
 *   .build();
 *
 * }</pre>
 *
 * Raft relies upon determinism to ensure consistency throughout the cluster, so <em>it is
 * imperative that each server in a cluster have the same state machine with the same commands.</em>
 * State machines are provided to the server as a {@link Supplier factory} to allow servers to
 * {@link RaftMember#promote(RaftMember.Type) transition} between stateful and stateless states.
 *
 * <h2>Storage</h2>
 *
 * By default, the log is stored on disk, but users can override the default {@link RaftStorage}
 * configuration via {@link RaftServer.Builder#withStorage(RaftStorage)}. Most notably, to configure
 * the storage module to store entries in memory instead of disk, configure the {@link
 * StorageLevel}.
 *
 * <pre>{@code
 * RaftServer server = RaftServer.builder(address)
 *   .withStateMachine(MyStateMachine::new)
 *   .withStorage(Storage.builder()
 *     .withDirectory(new File("logs"))
 *     .withStorageLevel(StorageLevel.DISK)
 *     .build())
 *   .build();
 * }</pre>
 *
 * Servers use the {@code Storage} object to manage the storage of cluster configurations, voting
 * information, and state machine snapshots in addition to logs. See the {@link RaftStorage}
 * documentation for more information.
 *
 * <h2>Bootstrapping the cluster</h2>
 *
 * Once a server has been built, it must be {@link #bootstrap() bootstrapped} to form a new cluster.
 *
 * <pre>{@code
 * CompletableFuture<RaftServer> future = server.bootstrap();
 * future.thenRun(() -> {
 *   System.out.println("Server bootstrapped!");
 * });
 *
 * }</pre>
 *
 * Alternatively, the bootstrapped cluster can include multiple servers by providing an initial
 * configuration to the {@link #bootstrap(MemberId...)} method on each server. When bootstrapping a
 * multi-node cluster, the bootstrap configuration must be identical on all servers for safety.
 *
 * <pre>{@code
 * List<Address> cluster = Arrays.asList(
 *   new Address("123.456.789.0", 5000),
 *   new Address("123.456.789.1", 5000),
 *   new Address("123.456.789.2", 5000)
 * );
 *
 * CompletableFuture<RaftServer> future = server.bootstrap(cluster);
 * future.thenRun(() -> {
 *   System.out.println("Cluster bootstrapped");
 * });
 *
 * }</pre>
 *
 * @see RaftStorage
 */
public interface RaftServer {

  /**
   * Returns a new Raft server builder using the default host:port.
   *
   * <p>The server will be constructed at 0.0.0.0:8700.
   *
   * @return The server builder.
   */
  static Builder builder() {
    try {
      final InetAddress address = InetAddress.getByName("0.0.0.0");
      return builder(MemberId.from(address.getHostName()));
    } catch (final UnknownHostException e) {
      throw new ConfigurationException(e, "Cannot configure local node %s", e.getMessage());
    }
  }

  /**
   * Returns a new Raft server builder.
   *
   * <p>The provided {@link MemberId} is the address to which to bind the server being constructed.
   *
   * @param localMemberId The local node identifier.
   * @return The server builder.
   */
  static Builder builder(final MemberId localMemberId) {
    return new DefaultRaftServer.Builder(localMemberId);
  }

  /**
   * Returns the server name.
   *
   * <p>The server name is provided to the server via the {@link Builder#withName(String) builder
   * configuration}. The name is used internally to manage the server's on-disk state. {@link
   * RaftLog Log}, {@link snapshot}, and {@link io.atomix.raft.storage.system.MetaStore
   * configuration} files stored on disk use the server name as the prefix.
   *
   * @return The server name.
   */
  String name();

  /**
   * Returns the server's cluster configuration.
   *
   * <p>The {@link RaftCluster} is representative of the server's current view of the cluster
   * configuration. The first time the server is {@link #bootstrap() started}, the cluster
   * configuration will be initialized using the {@link MemberId} list provided to the server {@link
   * #builder(MemberId) builder}. For {@link StorageLevel#DISK persistent} servers, subsequent
   * starts will result in the last known cluster configuration being loaded from disk.
   *
   * @return The server's cluster configuration.
   */
  RaftCluster cluster();

  /**
   * Adds a role change listener.
   *
   * @param listener The role change listener that consumes the role and the raft term.
   */
  void addRoleChangeListener(RaftRoleChangeListener listener);

  /**
   * Removes a role change listener.
   *
   * @param listener The role change listener to remove.
   */
  void removeRoleChangeListener(RaftRoleChangeListener listener);

  /** Adds a failure listener */
  void addFailureListener(Runnable failureListener);

  /** Removes a failure listener */
  void removeFailureListener(Runnable failureListener);

  /**
   * Bootstraps a single-node cluster.
   *
   * <p>Bootstrapping a single-node cluster results in the server forming a new cluster to which
   * additional servers can be joined.
   *
   * <p>Only {@link RaftMember.Type#ACTIVE} members can be included in a bootstrap configuration. If
   * the local server is not initialized as an active member, it cannot be part of the bootstrap
   * configuration for the cluster.
   *
   * <p>When the cluster is bootstrapped, the local server will be transitioned into the active
   * state and begin participating in the Raft consensus algorithm. When the cluster is first
   * bootstrapped, no leader will exist. The bootstrapped members will elect a leader amongst
   * themselves.
   *
   * <p>It is critical that all servers in a bootstrap configuration be started with the same exact
   * set of members. Bootstrapping multiple servers with different configurations may result in
   * split brain.
   *
   * <p>The {@link CompletableFuture} returned by this method will be completed once the cluster has
   * been bootstrapped, a leader has been elected, and the leader has been notified of the local
   * server's client configurations.
   *
   * @return A completable future to be completed once the cluster has been bootstrapped.
   */
  default CompletableFuture<RaftServer> bootstrap() {
    return bootstrap(Collections.emptyList());
  }

  /**
   * Bootstraps the cluster using the provided cluster configuration.
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
   * themselves.
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
  CompletableFuture<RaftServer> bootstrap(Collection<MemberId> cluster);

  /**
   * Bootstraps the cluster using the provided cluster configuration.
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
   * themselves.
   *
   * <p>It is critical that all servers in a bootstrap configuration be started with the same exact
   * set of members. Bootstrapping multiple servers with different configurations may result in
   * split brain.
   *
   * <p>The {@link CompletableFuture} returned by this method will be completed once the cluster has
   * been bootstrapped, a leader has been elected, and the leader has been notified of the local
   * server's client configurations.
   *
   * @param members The bootstrap cluster configuration.
   * @return A completable future to be completed once the cluster has been bootstrapped.
   */
  default CompletableFuture<RaftServer> bootstrap(final MemberId... members) {
    return bootstrap(Arrays.asList(members));
  }

  /**
   * Promotes the server to leader if possible.
   *
   * @return a future to be completed once the server has been promoted
   */
  CompletableFuture<RaftServer> promote();

  /**
   * Compacts server logs.
   *
   * @return a future to be completed once the server's logs have been compacted
   */
  CompletableFuture<Void> compact();

  /**
   * Shuts down the server without leaving the Raft cluster.
   *
   * @return A completable future to be completed once the server has been shutdown.
   */
  CompletableFuture<Void> shutdown();

  /**
   * Transitions the server to INACTIVE without shutting down or leaving the cluster.
   *
   * @return A completable future to be completed once the server is inactive.
   */
  CompletableFuture<Void> goInactive();

  /**
   * Returns the current Raft context.
   *
   * @return the current Raft context
   */
  RaftContext getContext();

  /**
   * Returns the server's term.
   *
   * @return the server's term
   */
  long getTerm();

  /**
   * Returns whether the server is a follower.
   *
   * @return whether the server is a follower
   */
  default boolean isFollower() {
    return getRole() == Role.FOLLOWER;
  }

  /**
   * Returns the server role.
   *
   * <p>The initial state of a Raft server is {@link Role#INACTIVE}. Once the server is {@link
   * #bootstrap() started} and until it is explicitly shutdown, the server will be in one of the
   * active states - {@link Role#PASSIVE}, {@link Role#FOLLOWER}, {@link Role#CANDIDATE}, or {@link
   * Role#LEADER}.
   *
   * @return The server role.
   */
  Role getRole();

  /**
   * Returns whether the server is the leader.
   *
   * @return whether the server is the leader
   */
  default boolean isLeader() {
    return getRole() == Role.LEADER;
  }

  /**
   * Returns a boolean indicating whether the server is running.
   *
   * @return Indicates whether the server is running.
   */
  boolean isRunning();

  /**
   * Steps down from the current leadership, which means tries to transition directly to follower.
   */
  CompletableFuture<Void> stepDown();

  /**
   * Builds a single-use Raft server.
   *
   * <p>This builder should be used to programmatically configure and construct a new {@link
   * RaftServer} instance. The builder provides methods for configuring all aspects of a Raft
   * server. The {@code RaftServer.Builder} class cannot be instantiated directly. To create a new
   * builder, use one of the {@link RaftServer#builder(MemberId) server builder factory} methods.
   *
   * <pre>{@code
   * RaftServer.Builder builder = RaftServer.builder(address);
   *
   * }</pre>
   *
   * Once the server has been configured, use the {@link #build()} method to build the server
   * instance:
   *
   * <pre>{@code
   * RaftServer server = RaftServer.builder(address)
   *   ...
   *   .build();
   *
   * }</pre>
   *
   * The state machine is the component of the server that stores state and reacts to commands and
   * queries submitted by clients to the cluster. State machines are provided to the server in the
   * form of a state machine {@link Supplier factory} to allow the server to reconstruct its state
   * when necessary.
   *
   * <pre>{@code
   * RaftServer server = RaftServer.builder(address)
   *   .withStateMachine(MyStateMachine::new)
   *   .build();
   *
   * }</pre>
   */
  abstract class Builder implements io.atomix.utils.Builder<RaftServer> {

    private static final Duration DEFAULT_ELECTION_TIMEOUT = Duration.ofMillis(750);
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(250);

    protected String name;
    protected MemberId localMemberId;
    protected ClusterMembershipService membershipService;
    protected RaftServerProtocol protocol;
    protected RaftStorage storage;
    protected RaftThreadContextFactory threadContextFactory;
    protected Duration electionTimeout = DEFAULT_ELECTION_TIMEOUT;
    protected Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
    protected Supplier<Random> randomFactory;
    protected Supplier<JournalIndex> journalIndexFactory;
    protected EntryValidator entryValidator = new NoopEntryValidator();
    protected int maxAppendsPerFollower = 2;
    protected int maxAppendBatchSize = 32 * 1024;

    protected Builder(final MemberId localMemberId) {
      this.localMemberId = checkNotNull(localMemberId, "localMemberId cannot be null");
    }

    /**
     * Sets the server name.
     *
     * <p>The server name is used to
     *
     * @param name The server name.
     * @return The server builder.
     */
    public Builder withName(final String name) {
      this.name = checkNotNull(name, "name cannot be null");
      return this;
    }

    /**
     * Sets the cluster membership service.
     *
     * @param membershipService the cluster membership service
     * @return the server builder
     */
    public Builder withMembershipService(final ClusterMembershipService membershipService) {
      this.membershipService = checkNotNull(membershipService, "membershipService cannot be null");
      return this;
    }

    /**
     * Sets the server protocol.
     *
     * @param protocol The server protocol.
     * @return The server builder.
     */
    public Builder withProtocol(final RaftServerProtocol protocol) {
      this.protocol = checkNotNull(protocol, "protocol cannot be null");
      return this;
    }

    /**
     * Sets the storage module.
     *
     * @param storage The storage module.
     * @return The Raft server builder.
     * @throws NullPointerException if {@code storage} is null
     */
    public Builder withStorage(final RaftStorage storage) {
      this.storage = checkNotNull(storage, "storage cannot be null");
      return this;
    }

    /**
     * Sets the threadContextFactory used to create raft threadContext
     *
     * @param threadContextFactory The RaftThreadContextFactory
     * @return The Raft server builder.
     * @throws NullPointerException if {@code threadContextFactory} is null
     */
    public Builder withThreadContextFactory(final RaftThreadContextFactory threadContextFactory) {
      this.threadContextFactory =
          checkNotNull(threadContextFactory, "threadContextFactory cannot be null");
      return this;
    }

    /**
     * Sets the factory that creates a {@link Random}. Raft uses it to randomize election timeouts.
     * This factory is useful in testing, when we want to control the execution.
     *
     * @return The Raft server builder.
     */
    public Builder withRandomFactory(final Supplier<Random> randomFactory) {
      this.randomFactory = checkNotNull(randomFactory, "randomFactory cannot be null");
      return this;
    }

    /**
     * Sets the Raft election timeout, returning the Raft configuration for method chaining.
     *
     * @param electionTimeout The Raft election timeout duration.
     * @return The Raft configuration.
     * @throws IllegalArgumentException If the election timeout is not positive
     * @throws NullPointerException if {@code electionTimeout} is null
     */
    public Builder withElectionTimeout(final Duration electionTimeout) {
      checkNotNull(electionTimeout, "electionTimeout cannot be null");
      checkArgument(
          !electionTimeout.isNegative() && !electionTimeout.isZero(),
          "electionTimeout must be positive");
      checkArgument(
          electionTimeout.toMillis() > heartbeatInterval.toMillis(),
          "electionTimeout must be greater than heartbeatInterval");
      this.electionTimeout = electionTimeout;
      return this;
    }

    /**
     * Sets the Raft heartbeat interval, returning the Raft configuration for method chaining.
     *
     * @param heartbeatInterval The Raft heartbeat interval duration.
     * @return The Raft configuration.
     * @throws IllegalArgumentException If the heartbeat interval is not positive
     * @throws NullPointerException if {@code heartbeatInterval} is null
     */
    public Builder withHeartbeatInterval(final Duration heartbeatInterval) {
      checkNotNull(heartbeatInterval, "heartbeatInterval cannot be null");
      checkArgument(
          !heartbeatInterval.isNegative() && !heartbeatInterval.isZero(),
          "sessionTimeout must be positive");
      checkArgument(
          heartbeatInterval.toMillis() < electionTimeout.toMillis(),
          "heartbeatInterval must be less than electionTimeout");
      this.heartbeatInterval = heartbeatInterval;
      return this;
    }

    /**
     * Sets the maximum append requests which are sent per follower at once. Default is 2.
     *
     * @param maxAppendsPerFollower the maximum appends send per follower
     * @return The server builder.
     */
    public Builder withMaxAppendsPerFollower(final int maxAppendsPerFollower) {
      checkArgument(maxAppendsPerFollower > 0, "maxAppendsPerFollower must be positive");
      this.maxAppendsPerFollower = maxAppendsPerFollower;
      return this;
    }

    /**
     * Sets the maximum batch size, which is sent per append request. Default size is 32 KB.
     *
     * @param maxAppendBatchSize the maximum batch size per append
     * @return The server builder.
     */
    public Builder withMaxAppendBatchSize(final int maxAppendBatchSize) {
      checkArgument(maxAppendBatchSize > 0, "maxAppendBatchSize must be positive");
      this.maxAppendBatchSize = maxAppendBatchSize;
      return this;
    }

    public Builder withJournalIndexFactory(final Supplier<JournalIndex> journalIndexFactory) {
      this.journalIndexFactory = journalIndexFactory;
      return this;
    }

    public Builder withEntryValidator(final EntryValidator entryValidator) {
      this.entryValidator = entryValidator;
      return this;
    }
  }

  /**
   * Raft server state types.
   *
   * <p>States represent the context of the server's internal state machine. Throughout the lifetime
   * of a server, the server will periodically transition between states based on requests,
   * responses, and timeouts.
   *
   * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
   */
  enum Role {

    /**
     * Represents the state of an inactive server.
     *
     * <p>All servers start in this state.
     */
    INACTIVE(false),

    /**
     * Represents the state of a server in the process of catching up its log.
     *
     * <p>Upon successfully joining an existing cluster, the server will transition to the passive
     * state and remain there until the leader determines that the server has caught up enough to be
     * promoted to a full member.
     */
    PASSIVE(false),

    /**
     * Represents the state of a server in the process of being promoted to an active voting member.
     */
    PROMOTABLE(false),

    /**
     * Represents the state of a server participating in normal log replication.
     *
     * <p>The follower state is a standard Raft state in which the server receives replicated log
     * entries from the leader.
     */
    FOLLOWER(true),

    /**
     * Represents the state of a server attempting to become the leader.
     *
     * <p>When a server in the follower state fails to receive communication from a valid leader for
     * some time period, the follower will transition to the candidate state. During this period,
     * the candidate requests votes from each of the other servers in the cluster. If the candidate
     * wins the election by receiving votes from a majority of the cluster, it will transition to
     * the leader state.
     */
    CANDIDATE(true),

    /**
     * Represents the state of a server which is actively coordinating and replicating logs with
     * other servers.
     *
     * <p>Leaders are responsible for handling and replicating writes from clients. Note that more
     * than one leader can exist at any given time, but Raft guarantees that no two leaders will
     * exist for the same {@link RaftCluster#getTerm()}.
     */
    LEADER(true);

    private final boolean active;

    Role(final boolean active) {
      this.active = active;
    }

    /**
     * Returns whether the role is a voting Raft member role.
     *
     * @return whether the role is a voting member
     */
    public boolean active() {
      return active;
    }
  }
}
