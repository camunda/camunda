/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension;

import jakarta.ws.rs.core.Response;
import java.util.stream.Stream;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpResponse;

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

  public static Stream<ErrorResponseMock> engineMockedErrorResponses() {
    return Stream.of(
        (request, times, mockServer) ->
            mockServer
                .when(request, times)
                .respond(
                    HttpResponse.response()
                        .withStatusCode(Response.Status.NOT_FOUND.getStatusCode())),
        (request, times, mockServer) ->
            mockServer
                .when(request, times)
                .respond(
                    HttpResponse.response()
                        .withStatusCode(Response.Status.FORBIDDEN.getStatusCode())),
        (request, times, mockServer) ->
            mockServer
                .when(request, times)
                .respond(
                    HttpResponse.response()
                        .withStatusCode(Response.Status.UNAUTHORIZED.getStatusCode())),
        (request, times, mockServer) ->
            mockServer
                .when(request, times)
                .respond(
                    HttpResponse.response()
                        .withStatusCode(Response.Status.BAD_REQUEST.getStatusCode())),
        (request, times, mockServer) ->
            mockServer
                .when(request, times)
                .error(HttpError.error().withResponseBytes(new byte[10])),
        (request, times, mockServer) ->
            mockServer
                .when(request, times)
                .error(HttpError.error().withDropConnection(true).withResponseBytes(new byte[10])));
  }
}
