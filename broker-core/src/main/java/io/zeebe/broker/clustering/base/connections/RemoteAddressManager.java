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
package io.zeebe.broker.clustering.base.connections;

import io.zeebe.broker.clustering.base.topology.*;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ClientTransport;

/**
 * Listens to topology member changes and adds / removes the remote addresses of the member's
 * management and replication apis on the corresponding client transports.
 */
public class RemoteAddressManager implements Service<Object>, TopologyMemberListener {
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<ClientTransport> managementClientTransportInjector = new Injector<>();
  private final Injector<ClientTransport> replicationClientTransportInjector = new Injector<>();

  private TopologyManager topologyManager;
  private ClientTransport managementClientTransport;
  private ClientTransport replicationClientTransport;

  @Override
  public void start(ServiceStartContext startContext) {
    topologyManager = topologyManagerInjector.getValue();
    managementClientTransport = managementClientTransportInjector.getValue();
    replicationClientTransport = replicationClientTransportInjector.getValue();

    topologyManager.addTopologyMemberListener(this);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    topologyManager.removeTopologyMemberListener(this);
  }

  @Override
  public Object get() {
    return null;
  }

  @Override
  public void onMemberAdded(final NodeInfo memberInfo, final Topology topology) {
    managementClientTransport.registerRemoteAddress(memberInfo.getManagementApiAddress());
    replicationClientTransport.registerRemoteAddress(memberInfo.getReplicationApiAddress());
  }

  @Override
  public void onMemberRemoved(final NodeInfo memberInfo, final Topology topology) {
    managementClientTransport.deactivateRemoteAddress(
        managementClientTransport.getRemoteAddress(memberInfo.getManagementApiAddress()));

    replicationClientTransport.deactivateRemoteAddress(
        replicationClientTransport.getRemoteAddress(memberInfo.getReplicationApiAddress()));
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }

  public Injector<ClientTransport> getManagementClientTransportInjector() {
    return managementClientTransportInjector;
  }

  public Injector<ClientTransport> getReplicationClientTransportInjector() {
    return replicationClientTransportInjector;
  }
}
