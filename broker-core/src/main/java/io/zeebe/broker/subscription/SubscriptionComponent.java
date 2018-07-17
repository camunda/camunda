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
package io.zeebe.broker.subscription;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;
import static io.zeebe.broker.subscription.SubscriptionServiceNames.MESSAGE_SERVICE_NAME;
import static io.zeebe.broker.subscription.SubscriptionServiceNames.SUBSCRIPTION_API_REQUEST_HANDLER_SERVICE_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.SUBSCRIPTION_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.bufferingServerTransport;
import static io.zeebe.broker.transport.TransportServiceNames.serverTransport;

import io.zeebe.broker.subscription.message.MessageService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.servicecontainer.ServiceContainer;

public class SubscriptionComponent implements Component {

  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final SubscriptionApiRequestHandlerService service = new SubscriptionApiRequestHandlerService();
    serviceContainer
        .createService(SUBSCRIPTION_API_REQUEST_HANDLER_SERVICE_NAME, service)
        .dependency(
            bufferingServerTransport(SUBSCRIPTION_API_SERVER_NAME),
            service.getServerTransportInjector())
        .install();

    final MessageService messageService = new MessageService();
    serviceContainer
        .createService(MESSAGE_SERVICE_NAME, messageService)
        .dependency(
            serverTransport(CLIENT_API_SERVER_NAME), messageService.getClientApiTransportInjector())
        .dependency(
            STREAM_PROCESSOR_SERVICE_FACTORY,
            messageService.getStreamProcessorServiceFactoryInjector())
        .groupReference(LEADER_PARTITION_GROUP_NAME, messageService.getPartitionsGroupReference())
        .install();
  }
}
