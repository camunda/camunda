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
package io.zeebe.broker.clustering.base.snapshots;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.TOPOLOGY_MANAGER_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.snapshotReplicationServiceName;
import static io.zeebe.broker.transport.TransportServiceNames.clientTransport;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.DurationUtil;
import java.time.Duration;
import org.slf4j.Logger;

public class SnapshotReplicationInstallService
    implements Service<SnapshotReplicationInstallService> {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private ServiceGroupReference<Partition> followerPartitionsGroupReference =
      ServiceGroupReference.<Partition>create().onAdd(this::onPartitionAdded).build();

  private ServiceStartContext startContext;
  private final Duration snapshotReplicationPeriod;

  public SnapshotReplicationInstallService(final BrokerCfg config) {
    this.snapshotReplicationPeriod =
        DurationUtil.parse(config.getData().getSnapshotReplicationPeriod());
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.startContext = startContext;
  }

  public void stop(ServiceStopContext stopContext) {}

  @Override
  public SnapshotReplicationInstallService get() {
    return this;
  }

  private void onPartitionAdded(
      final ServiceName<Partition> partitionServiceName, final Partition partition) {
    final ServiceName<SnapshotReplicationService> serviceName =
        snapshotReplicationServiceName(partition);
    final SnapshotReplicationService service =
        new SnapshotReplicationService(snapshotReplicationPeriod);

    if (!startContext.hasService(serviceName)) {
      LOG.debug("Installing snapshot replication service for {}", partition.getInfo());
      startContext
          .createService(serviceName, service)
          .dependency(partitionServiceName, service.getPartitionInjector())
          .dependency(TOPOLOGY_MANAGER_SERVICE, service.getTopologyManagerInjector())
          .dependency(
              clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME),
              service.getManagementClientApiInjector())
          .install();
    }
  }

  public ServiceGroupReference<Partition> getFollowerPartitionsGroupReference() {
    return followerPartitionsGroupReference;
  }
}
