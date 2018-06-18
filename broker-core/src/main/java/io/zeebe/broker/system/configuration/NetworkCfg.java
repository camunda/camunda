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
package io.zeebe.broker.system.configuration;

public class NetworkCfg implements ConfigurationEntry {
  private String host = "0.0.0.0";
  private String defaultSendBufferSize = "16M";

  private SocketBindingClientApiCfg client = new SocketBindingClientApiCfg();
  private SocketBindingManagementCfg management = new SocketBindingManagementCfg();
  private SocketBindingReplicationCfg replication = new SocketBindingReplicationCfg();

  @Override
  public void init(BrokerCfg brokerCfg, String brokerBase) {
    client.applyDefaults(this);
    management.applyDefaults(this);
    replication.applyDefaults(this);
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getDefaultSendBufferSize() {
    return defaultSendBufferSize;
  }

  public void setDefaultSendBufferSize(String defaultSendBufferSize) {
    this.defaultSendBufferSize = defaultSendBufferSize;
  }

  public SocketBindingClientApiCfg getClient() {
    return client;
  }

  public void setClient(SocketBindingClientApiCfg clientApi) {
    this.client = clientApi;
  }

  public SocketBindingManagementCfg getManagement() {
    return management;
  }

  public void setManagement(SocketBindingManagementCfg managementApi) {
    this.management = managementApi;
  }

  public SocketBindingReplicationCfg getReplication() {
    return replication;
  }

  public void setReplication(SocketBindingReplicationCfg replicationApi) {
    this.replication = replicationApi;
  }
}
