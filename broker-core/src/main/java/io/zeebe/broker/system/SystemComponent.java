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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_JOIN_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.FOLLOWER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.system.SystemServiceNames.BROKER_HEALTH_CHECK_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.BROKER_HTTP_SERVER;
import static io.zeebe.broker.system.SystemServiceNames.LEADER_MANAGEMENT_REQUEST_HANDLER;

import io.zeebe.broker.system.configuration.MetricsCfg;
import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.zeebe.broker.system.monitoring.BrokerHttpServerService;
import io.zeebe.servicecontainer.ServiceContainer;

public class SystemComponent implements Component {

  @Override
  public void init(final SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final BrokerHealthCheckService healthCheckService = new BrokerHealthCheckService();
    serviceContainer
        .createService(BROKER_HEALTH_CHECK_SERVICE, healthCheckService)
        .dependency(ATOMIX_SERVICE, healthCheckService.getAtomixInjector())
        .dependency(ATOMIX_JOIN_SERVICE)
        .groupReference(LEADER_PARTITION_GROUP_NAME, healthCheckService.getLeaderInstallReference())
        .groupReference(
            FOLLOWER_PARTITION_GROUP_NAME, healthCheckService.getFollowerInstallReference())
        .install();

    final MetricsCfg metricsCfg = context.getBrokerConfiguration().getMetrics();
    if (metricsCfg.isEnableHttpServer()) {
      serviceContainer
          .createService(
              BROKER_HTTP_SERVER, new BrokerHttpServerService(metricsCfg, healthCheckService))
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
