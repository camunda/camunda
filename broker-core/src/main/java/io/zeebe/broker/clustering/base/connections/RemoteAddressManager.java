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

import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.Topology;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyMemberListener;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientTransport;

/**
 * Listens to topology member changes and adds / removes the remote addresses of the member's
 * management and replication apis on the corresponding client transports.
 */
public class RemoteAddressManager implements Service<Void>, TopologyMemberListener {
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<ClientTransport> managementClientTransportInjector = new Injector<>();

  private TopologyManager topologyManager;
  private ClientTransport managementClientTransport;

  @Override
  public void start(ServiceStartContext startContext) {
    topologyManager = topologyManagerInjector.getValue();
    managementClientTransport = managementClientTransportInjector.getValue();
    topologyManager.addTopologyMemberListener(this);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    topologyManager.removeTopologyMemberListener(this);
  }

  @Override
  public Void get() {
    return null;
  }

  @Override
  public void onMemberAdded(final NodeInfo memberInfo, final Topology topology) {
    managementClientTransport.registerEndpoint(
        memberInfo.getNodeId(), memberInfo.getManagementApiAddress());
  }

  @Override
  public void onMemberRemoved(final NodeInfo memberInfo, final Topology topology) {
    managementClientTransport.deactivateEndpoint(memberInfo.getNodeId());
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }

  public Injector<ClientTransport> getManagementClientTransportInjector() {
    return managementClientTransportInjector;
  }
}
