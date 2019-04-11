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

import com.google.gson.GsonBuilder;
import io.zeebe.util.Environment;
import java.util.ArrayList;
import java.util.List;

public class BrokerCfg {

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private MetricsCfg metrics = new MetricsCfg();
  private DataCfg data = new DataCfg();
  private List<ExporterCfg> exporters = new ArrayList<>();
  private EmbeddedGatewayCfg gateway = new EmbeddedGatewayCfg();

  public void init(final String brokerBase) {
    init(brokerBase, new Environment());
  }

  public void init(final String brokerBase, final Environment environment) {
    network.init(this, brokerBase, environment);
    cluster.init(this, brokerBase, environment);
    threads.init(this, brokerBase, environment);
    metrics.init(this, brokerBase, environment);
    data.init(this, brokerBase, environment);
    exporters.forEach(e -> e.init(this, brokerBase, environment));
    gateway.init(this, brokerBase, environment);
  }

  public NetworkCfg getNetwork() {
    return network;
  }

  public void setNetwork(final NetworkCfg network) {
    this.network = network;
  }

  public ClusterCfg getCluster() {
    return cluster;
  }

  public void setCluster(final ClusterCfg cluster) {
    this.cluster = cluster;
  }

  public ThreadsCfg getThreads() {
    return threads;
  }

  public void setThreads(final ThreadsCfg threads) {
    this.threads = threads;
  }

  public MetricsCfg getMetrics() {
    return metrics;
  }

  public void setMetrics(final MetricsCfg metrics) {
    this.metrics = metrics;
  }

  public DataCfg getData() {
    return data;
  }

  public void setData(final DataCfg logs) {
    this.data = logs;
  }

  public List<ExporterCfg> getExporters() {
    return exporters;
  }

  public void setExporters(final List<ExporterCfg> exporters) {
    this.exporters = exporters;
  }

  public EmbeddedGatewayCfg getGateway() {
    return gateway;
  }

  public BrokerCfg setGateway(EmbeddedGatewayCfg gateway) {
    this.gateway = gateway;
    return this;
  }

  @Override
  public String toString() {
    return "BrokerCfg{"
        + "network="
        + network
        + ", cluster="
        + cluster
        + ", threads="
        + threads
        + ", metrics="
        + metrics
        + ", data="
        + data
        + ", exporters="
        + exporters
        + ", gateway="
        + gateway
        + '}';
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
