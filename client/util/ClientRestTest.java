/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.util;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.zeebe.client.ZeebeClient;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@WireMockTest
public abstract class ClientRestTest {

  public ZeebeClient client;
  public RestGatewayService gatewayService;

  @BeforeEach
  void beforeEach(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    client = createClient(mockInfo);
    gatewayService = new RestGatewayService(mockInfo);
  }

  @AfterEach
  void afterEach() {
    if (client != null) {
      client.close();
    }
  }

  private ZeebeClient createClient(final WireMockRuntimeInfo mockInfo) throws URISyntaxException {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .preferRestOverGrpc(true)
        .restAddress(new URI(mockInfo.getHttpBaseUrl()))
        .build();
  }
}
