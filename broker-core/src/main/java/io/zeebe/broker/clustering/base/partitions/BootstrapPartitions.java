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

import static io.zeebe.broker.clustering.base.partitions.Partition.getPartitionName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.partitionInstallServiceName;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.primitive.partition.Partition;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.StorageConfigurationManager;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Always installed on broker startup: reads configuration of all locally available partitions and
 * starts the corresponding services (logstream, partition ...)
 */
public class BootstrapPartitions implements Service<Void> {
  private final Injector<StorageConfigurationManager> configurationManagerInjector =
      new Injector<>();
  private final BrokerCfg brokerCfg;

  private StorageConfigurationManager configurationManager;
  private ServiceStartContext startContext;

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Atomix atomix;

  public BootstrapPartitions(final BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    configurationManager = configurationManagerInjector.getValue();
    atomix = atomixInjector.getValue();

    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup) atomix.getPartitionService().getPartitionGroup("raft-atomix");

    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();
    final List<Partition> owningPartitions =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .collect(Collectors.toList());

    this.startContext = startContext;
    startContext.run(
        () -> {
          final List<StorageConfiguration> configurations =
              configurationManager.getConfigurations().join();

          for (final StorageConfiguration configuration : configurations) {
            installPartition(startContext, configuration);
            owningPartitions.removeIf(
                partition -> partition.id().id() == configuration.getPartitionId());
          }

          for (int i = 0; i < owningPartitions.size(); i++) {
            installPartition(owningPartitions.get(i).id(), Collections.emptyList());
          }
        });
  }

  private void installPartition(final PartitionId partitionId, final List<Integer> members) {
    final StorageConfiguration configuration =
        configurationManager.createConfiguration(partitionId.id()).join();

    installPartition(startContext, configuration);
  }

  private void installPartition(
      final ServiceStartContext startContext, final StorageConfiguration configuration) {
    final String partitionName = getPartitionName(configuration.getPartitionId());
    final ServiceName<Void> partitionInstallServiceName =
        partitionInstallServiceName(partitionName);
    final String localMemberId = atomix.getMembershipService().getLocalMember().id().id();

    final PartitionInstallService partitionInstallService =
        new PartitionInstallService(
            atomix.getEventService(),
            atomix.getCommunicationService(),
            localMemberId,
            configuration,
            brokerCfg);

    startContext.createService(partitionInstallServiceName, partitionInstallService).install();
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<StorageConfigurationManager> getConfigurationManagerInjector() {
    return configurationManagerInjector;
  }

  public Injector<Atomix> getAtomixInjector() {
    return this.atomixInjector;
  }
}
