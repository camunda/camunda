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

import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.core.election.Leader;
import io.atomix.core.election.LeaderElection;
import io.atomix.core.election.LeadershipEvent;
import io.atomix.core.election.LeadershipEventListener;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.PrimitiveState;
import io.atomix.protocols.raft.MultiRaftProtocol;
import io.atomix.protocols.raft.ReadConsistency;
import io.zeebe.broker.Loggers;
import io.zeebe.distributedlog.impl.DistributedLogstreamName;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.distributedlog.restore.PartitionLeaderElectionController;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class PartitionLeaderElection extends Actor
    implements Service<PartitionLeaderElection>,
        LeadershipEventListener<String>,
        Consumer<PrimitiveState>,
        PartitionLeaderElectionController {

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Atomix atomix;

  private LeaderElection<String> election;

  private static final MultiRaftProtocol PROTOCOL =
      MultiRaftProtocol.builder()
          .withPartitioner(DistributedLogstreamName.getInstance())
          .withReadConsistency(
              ReadConsistency
                  .LINEARIZABLE) // guarantee that getLeadership always return the latest leader
          .build();

  private final int partitionId;
  private String memberId;
  private final List<PartitionRoleChangeListener> leaderElectionListeners;
  private boolean isLeader =
      false; // true if this node was the leader in the last leadership event received.
  private long leaderTerm; // current term if this node is the leader.
  private CompletableActorFuture<Void> startFuture;
  private boolean canBecomeLeader = true;

  public PartitionLeaderElection(int partitionId) {
    this.partitionId = partitionId;
    leaderElectionListeners = new ArrayList<>();
  }

  @Override
  public void start(ServiceStartContext startContext) {

    atomix = atomixInjector.getValue();
    memberId = atomix.getMembershipService().getLocalMember().id().id();

    LOG.info("Creating leader election for partition {} in node {}", partitionId, memberId);

    startFuture = new CompletableActorFuture<>();

    startContext.getScheduler().submitActor(this);
    startContext.async(startFuture, true);
  }

  @Override
  protected void onActorStarted() {
    initialize();
  }

  private void initialize() {
    final CompletableFuture<LeaderElection<String>> leaderElectionCompletableFuture =
        atomix
            .<String>leaderElectionBuilder(DistributedLogstreamName.getPartitionKey(partitionId))
            .withProtocol(PROTOCOL)
            .buildAsync();
    leaderElectionCompletableFuture.whenComplete(
        (leaderElection, error) -> {
          if (error == null) {
            election = leaderElection;
            actor.run(
                () -> {
                  final CompletableActorFuture<Void> joinFuture = new CompletableActorFuture<>();
                  tryJoinElection(joinFuture);
                  actor.runOnCompletion(joinFuture, (r, e) -> tryAddListener());
                });
            LogstreamConfig.putLeaderElectionController(memberId, partitionId, this);
          } else {
            LOG.debug(
                "Couldn't create leader election for partition {}, retrying.", partitionId, error);
            actor.run(this::initialize);
          }
        });
  }

  private void tryJoinElection(CompletableActorFuture<Void> joinFuture) {
    actor.runUntilDone(
        () -> {
          try {
            if (canBecomeLeader) {
              election.run(memberId);
            }
            joinFuture.complete(null);
            actor.done();
          } catch (PrimitiveException error) {
            LOG.debug(
                "Couldn't join leader election for partition {}, retrying.", partitionId, error);
            actor.yield();
          }
        });
  }

  private void tryAddListener() {
    actor.runUntilDone(
        () -> {
          try {
            election.addListener(this);
            updateLeadership();
            election.addStateChangeListener(this);
            startFuture.complete(null);
            actor.done();
          } catch (PrimitiveException error) {
            LOG.debug(
                "Couldn't add listener for leader election for partition {}, retrying.",
                partitionId,
                error);
            actor.yield();
          }
        });
  }

  private void updateLeadership() {
    try {
      final Leader<String> currentLeader = election.getLeadership().leader();
      if (currentLeader != null && memberId.equals(currentLeader.id())) {
        transitionToLeader(currentLeader.term());
      } else {
        transitionToFollower();
      }
    } catch (PrimitiveException e) {
      LOG.error(
          "Couldn't get current leadership for partition {}. Transitioning to follower.",
          partitionId,
          e);
      transitionToFollower();
    }
  }

  private void transitionToFollower() {
    isLeader = false;
    leaderElectionListeners.forEach(l -> l.onTransitionToFollower(partitionId));
  }

  private void transitionToLeader(long term) {
    leaderTerm = term;
    isLeader = true;
    leaderElectionListeners.forEach(l -> l.onTransitionToLeader(partitionId, term));
  }

  /**
   * Prevent this broker from becoming the stream processor leader for the partition until {@link
   * #join} is invoked. If it is currently the leader a new leader will be elected.
   */
  @Override
  public CompletableFuture<Void> withdraw() {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    actor.run(
        () -> {
          canBecomeLeader = false;
          try {
            election.withdraw(memberId);
            future.complete(null);
          } catch (Exception e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  /**
   * Join the election if it is not already joined. This broker can become stream processor leader
   * for the partition anytime from now.
   */
  @Override
  public CompletableFuture<Void> join() {
    final CompletableFuture<Void> result = new CompletableFuture<>();
    final CompletableActorFuture<Void> joinFuture = new CompletableActorFuture<>();
    actor.run(
        () -> {
          canBecomeLeader = true;
          tryJoinElection(joinFuture);

          actor.runOnCompletion(
              joinFuture,
              (nothing, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                } else {
                  result.complete(null);
                }
              });
        });

    return result;
  }

  @Override
  public MemberId getLeader() {
    final Leader<String> leader = election.getLeadership().leader();
    if (leader != null) {
      final Member member = atomix.getMembershipService().getMember(leader.id());
      if (member != null) {
        return member.id();
      }
    }

    return null;
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    election.async().removeListener(this);
    election.async().removeStateChangeListener(this);
    election.async().withdraw(memberId);
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

  @Override
  public void event(LeadershipEvent<String> leadershipEvent) {
    final Leader<String> newLeader = leadershipEvent.newLeadership().leader();
    onLeadershipChange(newLeader);
  }

  @Override
  public void accept(PrimitiveState primitiveState) {
    switch (primitiveState) {
        // If primitive is not in connected state, this node might miss leadership events. Hence for
        // safety, transition to follower.
      case CLOSED:
      case EXPIRED:
      case SUSPENDED:
        actor.run(
            () -> {
              if (isLeader) {
                transitionToFollower();
              }
            });
        break;
      case CONNECTED:
        try {
          final Leader<String> currentLeader = election.getLeadership().leader();
          onLeadershipChange(currentLeader);
        } catch (PrimitiveException e) {
          LOG.debug("Couldn't get current leadership for partition {}", partitionId, e);
          actor.run(
              () -> {
                if (isLeader) {
                  transitionToFollower();
                }
              });
        }
    }
  }

  private void onLeadershipChange(Leader<String> newLeader) {
    actor.run(
        () -> {
          final boolean isNewLeader = newLeader != null && memberId.equals(newLeader.id());

          final boolean becomeFollower = isLeader && !isNewLeader;
          final boolean becomeLeader = !isLeader && isNewLeader;

          if (becomeFollower) {
            transitionToFollower();
          } else if (becomeLeader) {
            transitionToLeader(newLeader.term());
          }
        });
  }

  /**
   * add listeners to get notified when the node transition between (stream processor) Leader and
   * Follower roles.
   */
  public void addListener(PartitionRoleChangeListener listener) {
    actor.run(
        () -> {
          leaderElectionListeners.add(listener);
          if (isLeader) {
            listener.onTransitionToLeader(partitionId, leaderTerm);
          } else {
            listener.onTransitionToFollower(partitionId);
          }
        });
  }

  public void removeListener(PartitionRoleChangeListener listener) {
    actor.run(
        () -> {
          leaderElectionListeners.remove(listener);
        });
  }

  public boolean isLeader() {
    return isLeader;
  }

  public LeaderElection<String> getElection() {
    return election;
  }
}
