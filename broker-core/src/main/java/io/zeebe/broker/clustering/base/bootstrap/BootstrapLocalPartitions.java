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
package io.zeebe.broker.clustering.base.bootstrap;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LOCAL_NODE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.partitionInstallServiceName;
import static io.zeebe.broker.transport.TransportServiceNames.REPLICATION_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.clientTransport;

import io.zeebe.broker.clustering.base.partitions.PartitionInstallService;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;

/**
 * Always installed on broker startup: reads configuration of all locally available partitions and
 * starts the corresponding services (raft, logstream, partition ...)
 */
public class BootstrapLocalPartitions implements Service<Object> {
  private final Injector<RaftPersistentConfigurationManager> configurationManagerInjector =
      new Injector<>();
  private final BrokerCfg brokerCfg;

  public BootstrapLocalPartitions(BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final RaftPersistentConfigurationManager configurationManager =
        configurationManagerInjector.getValue();

    startContext.run(
        () -> {
          final List<RaftPersistentConfiguration> configurations =
              configurationManager.getConfigurations().join();

          for (RaftPersistentConfiguration configuration : configurations) {
            installPartition(startContext, configuration);
          }
        });
  }

  private void installPartition(
      ServiceStartContext startContext, RaftPersistentConfiguration configuration) {
    final String partitionName =
        String.format(
            "%s-%d",
            BufferUtil.bufferAsString(configuration.getTopicName()),
            configuration.getPartitionId());
    final ServiceName<Void> partitionInstallServiceName =
        partitionInstallServiceName(partitionName);
    final boolean isInternalSystemPartition =
        configuration.getPartitionId() == Protocol.SYSTEM_PARTITION;

    final PartitionInstallService partitionInstallService =
        new PartitionInstallService(brokerCfg, configuration, isInternalSystemPartition);

    startContext
        .createService(partitionInstallServiceName, partitionInstallService)
        .dependency(LOCAL_NODE, partitionInstallService.getLocalNodeInjector())
        .dependency(
            clientTransport(REPLICATION_API_CLIENT_NAME),
            partitionInstallService.getClientTransportInjector())
        .install();
  }

  @Override
  public Object get() {
    return null;
  }

  public Injector<RaftPersistentConfigurationManager> getConfigurationManagerInjector() {
    return configurationManagerInjector;
  }
}
