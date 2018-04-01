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
package io.zeebe.broker.transport.cfg;

import io.zeebe.broker.clustering.ClusterComponentConfiguration;
import io.zeebe.broker.clustering.base.gossip.BrokerGossipConfiguration;
import io.zeebe.broker.system.ComponentConfiguration;
import io.zeebe.broker.system.GlobalConfiguration;
import io.zeebe.raft.RaftConfiguration;

public class TransportComponentCfg extends ComponentConfiguration
{
    public String host = "0.0.0.0";
    public int sendBufferSize = 16;
    public int defaultReceiveBufferSize = 16;

    public SocketBindingCfg clientApi = new SocketBindingCfg();
    public SocketBindingCfg managementApi = new SocketBindingCfg();
    public SocketBindingCfg replicationApi = new SocketBindingCfg();

    public BrokerGossipConfiguration gossip = new BrokerGossipConfiguration();
    public ClusterComponentConfiguration management = new ClusterComponentConfiguration();
    public RaftConfiguration raft = new RaftConfiguration();

    @Override
    public void applyGlobalConfiguration(GlobalConfiguration globalConfig)
    {
        management.applyGlobalConfiguration(globalConfig);
    }

}
