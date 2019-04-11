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
package io.zeebe.broker.system;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.system.SystemServiceNames.LEADER_MANAGEMENT_REQUEST_HANDLER;
import static io.zeebe.broker.system.SystemServiceNames.METRICS_FILE_WRITER;
import static io.zeebe.broker.system.SystemServiceNames.METRICS_HTTP_SERVER;

import io.zeebe.broker.system.configuration.MetricsCfg;
import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.broker.system.metrics.MetricsFileWriterService;
import io.zeebe.broker.system.metrics.MetricsHttpServerService;
import io.zeebe.servicecontainer.ServiceContainer;

public class SystemComponent implements Component {

  @Override
  public void init(final SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final MetricsCfg metricsCfg = context.getBrokerConfiguration().getMetrics();
    serviceContainer
        .createService(METRICS_FILE_WRITER, new MetricsFileWriterService(metricsCfg))
        .install();

    if (metricsCfg.isEnableHttpServer()) {
      serviceContainer
          .createService(METRICS_HTTP_SERVER, new MetricsHttpServerService(metricsCfg))
          .install();
    }

    final LeaderManagementRequestHandler requestHandlerService =
        new LeaderManagementRequestHandler();
    serviceContainer
        .createService(LEADER_MANAGEMENT_REQUEST_HANDLER, requestHandlerService)
        .dependency(ATOMIX_SERVICE, requestHandlerService.getAtomixInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, requestHandlerService.getLeaderPartitionsGroupReference())
        .install();
  }
}
