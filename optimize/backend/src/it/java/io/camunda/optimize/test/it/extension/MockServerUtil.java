/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;

public class MockServerUtil {

  public static final String MOCKSERVER_HOST = "localhost";

  private MockServerUtil() {}

  public static ClientAndServer createProxyMockServer(
      final String targetHost, final int targetPort, final int mockServerPort) {
    ConfigurationProperties.logLevel("INFO");
    ConfigurationProperties.maxExpectations(10);
    ConfigurationProperties.maxLogEntries(250);

    final ClientAndServer mockServer =
        ClientAndServer.startClientAndServer(targetHost, targetPort, mockServerPort);
    Runtime.getRuntime().addShutdownHook(new Thread(mockServer::stop));
    return mockServer;
  }
}
