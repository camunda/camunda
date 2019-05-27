/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.partitions;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.RaftServer.Role;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.zeebe.broker.Loggers;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class PartitionLeaderElection extends Actor
    implements Service<PartitionLeaderElection>, Consumer<Role> {

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Injector<Atomix> atomixInjector = new Injector<>();

  private final int partitionId;
  private final RaftPartition partition;
  private final List<PartitionRoleChangeListener> leaderElectionListeners;
  private Role raftRole;
  private long leaderTerm; // current term if this node is the leader.
  private CompletableActorFuture<Void> startFuture;

  public PartitionLeaderElection(RaftPartition partition) {
    this.partition = partition;
    this.partitionId = partition.id().id();
    leaderElectionListeners = new ArrayList<>();
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final Atomix atomix = atomixInjector.getValue();
    final MemberId memberId = atomix.getMembershipService().getLocalMember().id();

    startFuture = new CompletableActorFuture<>();
    startContext.getScheduler().submitActor(this);
    startContext.async(startFuture, true);

    LOG.info("Creating leader election for partition {} in node {}", partitionId, memberId);
  }

  @Override
  protected void onActorStarted() {
    partition.addRoleChangeListener(this);
    onRoleChange(partition.getRole());
    startFuture.complete(null);
  }

  @Override
  public void accept(Role newRole) {
    actor.run(() -> onRoleChange(newRole));
  }

  private void onRoleChange(Role newRole) {
    switch (newRole) {
      case LEADER:
        if (raftRole != Role.LEADER) {
          transitionToLeader(partition.term());
        }
        break;
      case INACTIVE:
      case PASSIVE:
      case PROMOTABLE:
      case CANDIDATE:
      case FOLLOWER:
      default:
        if (raftRole == null || raftRole == Role.LEADER) {
          transitionToFollower();
        }
        break;
    }

    LOG.debug("Partition role transitioning from {} to {}", raftRole, newRole);
    raftRole = newRole;
  }

  private void transitionToFollower() {
    leaderElectionListeners.forEach(l -> l.onTransitionToFollower(partitionId));
  }

  private void transitionToLeader(long term) {
    leaderTerm = term;
    leaderElectionListeners.forEach(l -> l.onTransitionToLeader(partitionId, term));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    partition.removeRoleChangeListener(this);
    actor.close();
  }

  @Override
  public PartitionLeaderElection get() {
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }

  /**
   * add listeners to get notified when the node transition between (stream processor) Leader and
   * Follower roles.
   */
  public void addListener(PartitionRoleChangeListener listener) {
    actor.run(
        () -> {
          leaderElectionListeners.add(listener);
          if (raftRole == Role.LEADER) {
            listener.onTransitionToLeader(partitionId, leaderTerm);
          } else {
            listener.onTransitionToFollower(partitionId);
          }
        });
  }

  public void removeListener(PartitionRoleChangeListener listener) {
    actor.run(() -> leaderElectionListeners.remove(listener));
  }
}
