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
package io.zeebe.broker.system.monitoring;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class BrokerHttpServerService implements Service<BrokerHttpServer> {

  private final String host;
  private final int port;
  private BrokerHealthCheckService brokerHealthCheckService;

  private BrokerHttpServer brokerHttpServer;

  public BrokerHttpServerService(
      String host, int port, BrokerHealthCheckService brokerHealthCheckService) {
    this.host = host;
    this.port = port;
    this.brokerHealthCheckService = brokerHealthCheckService;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    startContext.run(
        () -> brokerHttpServer = new BrokerHttpServer(brokerHealthCheckService, host, port));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.run(brokerHttpServer::close);
  }

  @Override
  public BrokerHttpServer get() {
    return brokerHttpServer;
  }
}
