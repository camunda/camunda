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

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.raft.RaftConfiguration;
import java.util.ArrayList;
import java.util.List;

public class BrokerCfg {
  private int bootstrap = 0;

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();
  private MetricsCfg metrics = new MetricsCfg();
  private DataCfg data = new DataCfg();
  private GossipConfiguration gossip = new GossipConfiguration();
  private RaftConfiguration raft = new RaftConfiguration();
  private List<TopicCfg> topics = new ArrayList<>();

  public void init(String brokerBase) {
    network.init(this, brokerBase);
    cluster.init(this, brokerBase);
    threads.init(this, brokerBase);
    metrics.init(this, brokerBase);
    data.init(this, brokerBase);
  }

  public int getBootstrap() {
    return bootstrap;
  }

  public void setBootstrap(int bootstrap) {
    this.bootstrap = bootstrap;
  }

  public NetworkCfg getNetwork() {
    return network;
  }

  public void setNetwork(NetworkCfg network) {
    this.network = network;
  }

  public ClusterCfg getCluster() {
    return cluster;
  }

  public void setCluster(ClusterCfg cluster) {
    this.cluster = cluster;
  }

  public ThreadsCfg getThreads() {
    return threads;
  }

  public void setThreads(ThreadsCfg threads) {
    this.threads = threads;
  }

  public MetricsCfg getMetrics() {
    return metrics;
  }

  public void setMetrics(MetricsCfg metrics) {
    this.metrics = metrics;
  }

  public DataCfg getData() {
    return data;
  }

  public void setData(DataCfg logs) {
    this.data = logs;
  }

  public GossipConfiguration getGossip() {
    return gossip;
  }

  public void setGossip(GossipConfiguration gossip) {
    this.gossip = gossip;
  }

  public RaftConfiguration getRaft() {
    return raft;
  }

  public void setRaft(RaftConfiguration raft) {
    this.raft = raft;
  }

  public List<TopicCfg> getTopics() {
    return topics;
  }

  public void setTopics(List<TopicCfg> topics) {
    this.topics = topics;
  }
}
