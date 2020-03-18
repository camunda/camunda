/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;

public class MockServerFactory {

  public static ClientAndServer createProxyMockServer(String targetHost, int targetPort, int mockServerPort) {
    ConfigurationProperties.logLevel("INFO");
    ConfigurationProperties.maxExpectations(10);
    ConfigurationProperties.maxLogEntries(250);
    
    final ClientAndServer mockServer = ClientAndServer.startClientAndServer(
      targetHost,
      targetPort,
      mockServerPort
    );
    Runtime.getRuntime().addShutdownHook(new Thread(mockServer::stop));
    return mockServer;
  }

}
