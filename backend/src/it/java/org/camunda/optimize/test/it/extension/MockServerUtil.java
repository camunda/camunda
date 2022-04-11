/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpResponse;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MockServerUtil {

  public static final String MOCKSERVER_HOST = "localhost";

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

  public static Stream<ErrorResponseMock> engineMockedErrorResponses() {
    return Stream.of(
      (request, times, mockServer) -> mockServer.when(request, times)
        .respond(HttpResponse.response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())),
      (request, times, mockServer) -> mockServer.when(request, times)
        .respond(HttpResponse.response().withStatusCode(Response.Status.FORBIDDEN.getStatusCode())),
      (request, times, mockServer) -> mockServer.when(request, times)
        .respond(HttpResponse.response().withStatusCode(Response.Status.UNAUTHORIZED.getStatusCode())),
      (request, times, mockServer) -> mockServer.when(request, times)
        .respond(HttpResponse.response().withStatusCode(Response.Status.BAD_REQUEST.getStatusCode())),
      (request, times, mockServer) -> mockServer.when(request, times)
        .error(HttpError.error().withResponseBytes(new byte[10])),
      (request, times, mockServer) -> mockServer.when(request, times)
        .error(HttpError.error().withDropConnection(true).withResponseBytes(new byte[10]))
    );
  }

}
