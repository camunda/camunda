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
package io.zeebe.broker.system.monitoring;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.primitive.partition.PartitionGroup;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.sched.Actor;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class BrokerHealthCheckService extends Actor implements Service<Void> {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private Map<Integer, Boolean> partitionInstallStatus;

  /* set to true when all partitions are installed. Once set to true, it is never
  changed. */
  private volatile boolean brokerStarted = false;

  private final ServiceGroupReference<Partition> leaderInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((name, partition) -> updateBrokerReadyStatus(partition.getPartitionId()))
          .build();

  private final ServiceGroupReference<Partition> followerInstallReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((name, partition) -> updateBrokerReadyStatus(partition.getPartitionId()))
          .build();

  private Atomix atomix;
  private final Injector<Atomix> atomixInjector = new Injector<>();

  public boolean isBrokerReady() {
    return brokerStarted;
  }

  private void updateBrokerReadyStatus(int partitionId) {
    actor.call(
        () -> {
          if (!brokerStarted) {
            partitionInstallStatus.put(partitionId, true);
            brokerStarted = !partitionInstallStatus.containsValue(false);

            LOG.info("Partition '{}' is installed.", partitionId);

            if (brokerStarted) {
              LOG.info("All partitions are installed. Broker is ready!");
            }
          }
        });
  }

  private void initializePartitionInstallStatus() {
    final PartitionGroup partitionGroup =
        atomix.getPartitionService().getPartitionGroup(Partition.GROUP_NAME);
    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();

    partitionInstallStatus =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(partition -> partition.id().id())
            .collect(Collectors.toMap(Function.identity(), p -> false));
  }

  @Override
  public void start(ServiceStartContext startContext) {
    atomix = atomixInjector.getValue();
    initializePartitionInstallStatus();
    startContext.getScheduler().submitActor(this);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    actor.close();
  }

  @Override
  public Void get() {
    return null;
  }

  public ServiceGroupReference<Partition> getLeaderInstallReference() {
    return leaderInstallReference;
  }

  public ServiceGroupReference<Partition> getFollowerInstallReference() {
    return followerInstallReference;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
