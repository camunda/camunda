/*
 * Copyright © 2019 camunda services GmbH (info@camunda.com)
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
package io.zeebe;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import io.prometheus.client.exporter.HTTPServer;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.Topology;
import io.zeebe.config.AppCfg;
import java.io.IOException;
import java.util.function.Function;
import me.dinowernli.grpc.prometheus.Configuration;
import me.dinowernli.grpc.prometheus.MonitoringClientInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class App implements Runnable {

  protected static MonitoringClientInterceptor monitoringInterceptor;
  private static final Logger LOG = LoggerFactory.getLogger(App.class);
  private static HTTPServer monitoringServer;

  static void createApp(Function<AppCfg, Runnable> appFactory) {
    final Config config = ConfigFactory.load().getConfig("app");
    LOG.info("Starting app with config: {}", config.root().render());
    final AppCfg appCfg = ConfigBeanFactory.create(config, AppCfg.class);
    startMonitoringServer(appCfg);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> stopMonitoringServer()));
    monitoringInterceptor = MonitoringClientInterceptor.create(Configuration.allMetrics());
    appFactory.apply(appCfg).run();
  }

  private static void startMonitoringServer(AppCfg appCfg) {
    try {
      monitoringServer = new HTTPServer(appCfg.getMonitoringPort());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void stopMonitoringServer() {
    if (monitoringServer != null) {
      monitoringServer.stop();
    }
  }

  protected void printTopology(ZeebeClient client) {
    while (true) {
      try {
        final Topology topology = client.newTopologyRequest().send().join();
        topology
            .getBrokers()
            .forEach(
                b -> {
                  LOG.info("Broker {} - {}", b.getNodeId(), b.getAddress());
                  b.getPartitions()
                      .forEach(p -> LOG.info("{} - {}", p.getPartitionId(), p.getRole()));
                });
        break;
      } catch (Exception e) {
        // retry
      }
    }
  }
}
