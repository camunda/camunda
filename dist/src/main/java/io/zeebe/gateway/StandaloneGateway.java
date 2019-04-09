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
package io.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.zeebe.gateway.impl.configuration.ClusterCfg;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.util.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Paths;

public class StandaloneGateway {

  private final AtomixCluster atomixCluster;
  private final Gateway gateway;

  public StandaloneGateway(GatewayCfg gatewayCfg) {
    atomixCluster = createAtomixCluster(gatewayCfg.getCluster());
    gateway = new Gateway(gatewayCfg, atomixCluster);
  }

  private AtomixCluster createAtomixCluster(ClusterCfg clusterCfg) {
    final AtomixCluster atomixCluster =
        AtomixCluster.builder()
            .withMemberId(clusterCfg.getMemberId())
            .withAddress(Address.from(clusterCfg.getHost(), clusterCfg.getPort()))
            .withClusterId(clusterCfg.getClusterName())
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder()
                    .withNodes(Address.from(clusterCfg.getContactPoint()))
                    .build())
            .build();

    atomixCluster.start();

    return atomixCluster;
  }

  public void run() throws IOException, InterruptedException {
    gateway.listenAndServe();
    atomixCluster.stop();
  }

  public static void main(String args[]) throws Exception {
    final GatewayCfg gatewayCfg = initConfiguration(args);
    gatewayCfg.init();
    new StandaloneGateway(gatewayCfg).run();
  }

  private static GatewayCfg initConfiguration(String[] args) {
    if (args.length >= 1) {
      String configFileLocation = args[0];

      if (!Paths.get(configFileLocation).isAbsolute()) {
        configFileLocation =
            Paths.get(getBasePath(), configFileLocation).toAbsolutePath().normalize().toString();
      }

      return TomlConfigurationReader.read(configFileLocation, GatewayCfg.class);
    } else {
      return new GatewayCfg();
    }
  }

  private static String getBasePath() {
    String basePath = System.getProperty("basedir");

    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }

    return basePath;
  }
}
