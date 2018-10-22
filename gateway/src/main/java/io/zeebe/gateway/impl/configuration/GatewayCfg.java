/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.configuration;

import com.google.gson.GsonBuilder;
import io.zeebe.util.Environment;
import java.util.Objects;

public class GatewayCfg {

  private NetworkCfg network = new NetworkCfg();
  private ClusterCfg cluster = new ClusterCfg();
  private ThreadsCfg threads = new ThreadsCfg();

  public void init() {
    init(new Environment());
  }

  public void init(Environment environment) {
    init(environment, ConfigurationDefaults.DEFAULT_HOST);
  }

  public void init(Environment environment, String defaultHost) {
    network.init(environment, defaultHost);
    cluster.init(environment);
    threads.init(environment);
  }

  public NetworkCfg getNetwork() {
    return network;
  }

  public GatewayCfg setNetwork(NetworkCfg network) {
    this.network = network;
    return this;
  }

  public ClusterCfg getCluster() {
    return cluster;
  }

  public GatewayCfg setCluster(ClusterCfg cluster) {
    this.cluster = cluster;
    return this;
  }

  public ThreadsCfg getThreads() {
    return threads;
  }

  public GatewayCfg setThreads(ThreadsCfg threads) {
    this.threads = threads;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GatewayCfg that = (GatewayCfg) o;
    return Objects.equals(network, that.network)
        && Objects.equals(cluster, that.cluster)
        && Objects.equals(threads, that.threads);
  }

  @Override
  public int hashCode() {
    return Objects.hash(network, cluster, threads);
  }

  @Override
  public String toString() {
    return "GatewayCfg{"
        + "networkCfg="
        + network
        + ", clusterCfg="
        + cluster
        + ", threadsCfg="
        + threads
        + '}';
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
