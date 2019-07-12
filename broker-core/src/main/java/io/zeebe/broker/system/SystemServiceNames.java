/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system;

import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.broker.system.monitoring.BrokerHttpServer;
import io.zeebe.servicecontainer.ServiceName;

public class SystemServiceNames {

  public static final ServiceName<BrokerHttpServer> BROKER_HTTP_SERVER =
      ServiceName.newServiceName("broker.httpServer", BrokerHttpServer.class);

  public static final ServiceName<LeaderManagementRequestHandler>
      LEADER_MANAGEMENT_REQUEST_HANDLER =
          ServiceName.newServiceName(
              "broker.system.management.requestHandler", LeaderManagementRequestHandler.class);

  public static final ServiceName<Void> BROKER_HEALTH_CHECK_SERVICE =
      ServiceName.newServiceName("broker.system.monitoring.healthcheck", Void.class);
}
