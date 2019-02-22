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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.FOLLOWER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.followerPartitionServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.leaderOpenLogStreamServiceName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.leaderPartitionServiceName;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.stateStorageFactoryServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamServiceName;

import io.atomix.core.Atomix;
import io.atomix.core.election.Leader;
import io.atomix.core.election.LeaderElection;
import io.atomix.core.election.LeadershipEvent;
import io.atomix.core.election.LeadershipEventListener;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.logstreams.state.StateStorageFactory;
import io.zeebe.logstreams.impl.service.LeaderOpenLogStreamAppenderService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import org.slf4j.Logger;

// Listen to leadership changes and install the services
public class PartitionRoleChangeListener implements Service<Void>, LeadershipEventListener<String> {

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private final Injector<LeaderElection> electionInjector = new Injector<>();
  private final Injector<LogStream> logStreamInjector = new Injector<>();

  private Atomix atomix;
  private LeaderElection<String> election;
  private LogStream logStream;
  private String logName;
  private String memberId;
  private final PartitionInfo partitionInfo;

  private ServiceStartContext startContext;
  private ServiceName<LogStream> logStreamServiceName;
  private ServiceName<StateStorageFactory> stateStorageFactoryServiceName;
  private ServiceName<Void> openLogStreamServiceName;
  private ServiceName<Partition> leaderPartitionServiceName;
  private ServiceName<Partition> followerPartitionServiceName;

  public PartitionRoleChangeListener(PartitionInfo partitionInfo) {
    this.partitionInfo = partitionInfo;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.startContext = startContext;
    atomix = atomixInjector.getValue();
    election = electionInjector.getValue();
    logStream = logStreamInjector.getValue();
    logName = logStream.getLogName();
    memberId = atomix.getMembershipService().getLocalMember().id().id();

    logStreamServiceName = logStreamServiceName(logName);
    stateStorageFactoryServiceName = stateStorageFactoryServiceName(logName);

    leaderPartitionServiceName = leaderPartitionServiceName(logName);
    openLogStreamServiceName = leaderOpenLogStreamServiceName(logName);

    followerPartitionServiceName = followerPartitionServiceName(logName);

    election.addListener(this);

    // In case it misses the events occurred before the listener was added
    final Leader<String> currentLeader = election.getLeadership().leader();
    final boolean isLeader = currentLeader != null && currentLeader.id().equals(memberId);
    if (isLeader) {
      installLeaderPartition();
    } else {
      installFollowerPartition();
    }
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    election.removeListener(this);
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }

  public Injector<LeaderElection> getElectionInjector() {
    return electionInjector;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }

  @Override
  public void event(LeadershipEvent<String> leadershipEvent) {
    final Leader<String> oldLeader = leadershipEvent.oldLeadership().leader();
    final Leader<String> newLeader = leadershipEvent.newLeadership().leader();
    if (newLeader == null) {
      // TODO: check if leader can be null during transition
      return;
    }

    final boolean leadershipChanged = !newLeader.equals(oldLeader);
    if (leadershipChanged) {
      final boolean wasLeader = oldLeader != null && memberId.equals(oldLeader.id());
      final boolean becomeLeader = memberId.equals(newLeader.id());
      if (wasLeader) {
        transitionToFollower();
      } else if (becomeLeader) {
        transitionToLeader();
      }
    }
  }

  private void transitionToLeader() {
    removeFollowerPartitionService();
    installLeaderPartition();
  }

  private void transitionToFollower() {
    removeLeaderPartitionService();
    installFollowerPartition();
  }

  private void removeLeaderPartitionService() {
    if (!startContext.hasService(leaderPartitionServiceName)) {
      startContext.removeService(openLogStreamServiceName);
      startContext.removeService(leaderPartitionServiceName);
    }
  }

  private void installLeaderPartition() {
    if (!startContext.hasService(leaderPartitionServiceName)) {
      LOG.debug(
          "Installing leader partition service for partition {}", partitionInfo.getPartitionId());
      final Partition partition = new Partition(partitionInfo, RaftState.LEADER);
      final ServiceName<Void> openLogStreamServiceName = leaderOpenLogStreamServiceName(logName);

      startContext
          .createService(
              openLogStreamServiceName, new LeaderOpenLogStreamAppenderService(logStream))
          .install();

      startContext
          .createService(leaderPartitionServiceName, partition)
          .dependency(openLogStreamServiceName)
          .dependency(logStreamServiceName, partition.getLogStreamInjector())
          .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
          .group(LEADER_PARTITION_GROUP_NAME)
          .install();
    }
  }

  private void installFollowerPartition() {
    if (!startContext.hasService(followerPartitionServiceName)) {
      LOG.debug(
          "Installing follower partition service for partition {}", partitionInfo.getPartitionId());
      final Partition partition = new Partition(partitionInfo, RaftState.FOLLOWER);
      startContext
          .createService(followerPartitionServiceName, partition)
          .dependency(logStreamServiceName, partition.getLogStreamInjector())
          .dependency(stateStorageFactoryServiceName, partition.getStateStorageFactoryInjector())
          .group(FOLLOWER_PARTITION_GROUP_NAME)
          .install();
    }
  }

  private void removeFollowerPartitionService() {
    if (startContext.hasService(followerPartitionServiceName)) {
      LOG.debug("Removing follower partition service for partition {}", partitionInfo);
      startContext.removeService(followerPartitionServiceName);
    }
  }
}
