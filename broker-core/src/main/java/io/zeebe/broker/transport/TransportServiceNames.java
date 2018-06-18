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
package io.zeebe.broker.transport;

import io.zeebe.broker.transport.clientapi.ClientApiMessageHandler;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManager;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.raft.RaftApiMessageHandler;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.*;

public class TransportServiceNames {
  public static final ServiceName<ClientApiMessageHandler> CLIENT_API_MESSAGE_HANDLER =
      ServiceName.newServiceName(
          "transport.clientApi.messageHandler", ClientApiMessageHandler.class);
  public static final ServiceName<ControlMessageHandlerManager> CONTROL_MESSAGE_HANDLER_MANAGER =
      ServiceName.newServiceName(
          "transport.clientApi.controlMessage", ControlMessageHandlerManager.class);
  public static final ServiceName<RaftApiMessageHandler> REPLICATION_API_MESSAGE_HANDLER =
      ServiceName.newServiceName(
          "transport.replicationApi.messageHandler", RaftApiMessageHandler.class);

  public static final String CLIENT_API_SERVER_NAME = "clientApi.server";
  public static final String MANAGEMENT_API_SERVER_NAME = "managementApi.server";
  public static final String REPLICATION_API_SERVER_NAME = "replicationApi.server";
  public static final String MANAGEMENT_API_CLIENT_NAME = "managementApi.client";
  public static final String REPLICATION_API_CLIENT_NAME = "replicationApi.client";

  public static ServiceName<Dispatcher> receiveBufferName(String identifier) {
    return ServiceName.newServiceName(
        String.format("transport.%s.receive-buffer", identifier), Dispatcher.class);
  }

  public static ServiceName<ServerTransport> serverTransport(String identifier) {
    return ServiceName.newServiceName(
        String.format("transport.%s.server", identifier), ServerTransport.class);
  }

  public static ServiceName<BufferingServerTransport> bufferingServerTransport(String identifier) {
    return ServiceName.newServiceName(
        String.format("transport.%s.buffering-server", identifier), BufferingServerTransport.class);
  }

  public static ServiceName<ClientTransport> clientTransport(String identifier) {
    return ServiceName.newServiceName(
        String.format("transport.%s.client", identifier), ClientTransport.class);
  }
}
