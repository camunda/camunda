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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_SYSTEM_GROUP_NAME;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;
import static io.zeebe.broker.system.SystemServiceNames.*;
import static io.zeebe.broker.transport.TransportServiceNames.*;

import io.zeebe.broker.system.metrics.MetricsFileWriterService;
import io.zeebe.broker.system.workflow.repository.api.management.DeploymentManagerRequestHandler;
import io.zeebe.broker.system.workflow.repository.service.DeploymentManager;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.ServiceContainer;

public class SystemComponent implements Component {
  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final MetricsFileWriterService metricsFileWriterService =
        new MetricsFileWriterService(context.getBrokerConfiguration().getMetrics());
    serviceContainer.createService(METRICS_FILE_WRITER, metricsFileWriterService).install();

    final DeploymentManagerRequestHandler requestHandlerService =
        new DeploymentManagerRequestHandler();
    serviceContainer
        .createService(DEPLOYMENT_MANAGER_REQUEST_HANDLER, requestHandlerService)
        .dependency(
            bufferingServerTransport(MANAGEMENT_API_SERVER_NAME),
            requestHandlerService.getManagementApiServerTransportInjector())
        .install();

    final DeploymentManager deploymentManagerService = new DeploymentManager();
    serviceContainer
        .createService(DEPLOYMENT_MANAGER_SERVICE, deploymentManagerService)
        .dependency(
            DEPLOYMENT_MANAGER_REQUEST_HANDLER,
            deploymentManagerService.getRequestHandlerServiceInjector())
        .dependency(
            STREAM_PROCESSOR_SERVICE_FACTORY,
            deploymentManagerService.getStreamProcessorServiceFactoryInjector())
        .dependency(
            serverTransport(CLIENT_API_SERVER_NAME),
            deploymentManagerService.getClientApiTransportInjector())
        .dependency(
            TransportServiceNames.CONTROL_MESSAGE_HANDLER_MANAGER,
            deploymentManagerService.getControlMessageHandlerManagerServiceInjector())
        .groupReference(
            LEADER_PARTITION_SYSTEM_GROUP_NAME,
            deploymentManagerService.getPartitionsGroupReference())
        .install();
  }
}
