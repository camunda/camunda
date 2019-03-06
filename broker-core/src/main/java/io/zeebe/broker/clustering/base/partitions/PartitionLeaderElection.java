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

import io.atomix.core.Atomix;
import io.atomix.core.election.LeaderElection;
import io.atomix.protocols.raft.MultiRaftProtocol;
import io.zeebe.broker.Loggers;
import io.zeebe.distributedlog.impl.DistributedLogstreamName;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import org.slf4j.Logger;

public class PartitionLeaderElection implements Service<LeaderElection> {

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Atomix atomix;

  // TODO: Check if we should use memberId instead of string
  private LeaderElection<String> election;

  private static final MultiRaftProtocol PROTOCOL =
      MultiRaftProtocol.builder().withPartitioner(DistributedLogstreamName.getInstance()).build();

  private final int partitionId;
  private String memberId;

  public PartitionLeaderElection(int partitionId) {
    this.partitionId = partitionId;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    atomix = atomixInjector.getValue();
    memberId = atomix.getMembershipService().getLocalMember().id().id();

    LOG.info("Creating leader election for partition {} in node {}", partitionId, memberId);

    election =
        atomix
            .<String>leaderElectionBuilder(DistributedLogstreamName.getPartitionKey(partitionId))
            .withProtocol(PROTOCOL)
            .build();

    election.run(memberId);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    election.withdraw(memberId);
  }

  @Override
  public LeaderElection<String> get() {
    return election;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
