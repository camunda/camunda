/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public final class BrokerHttpServerTest {

  @ClassRule public static final EmbeddedBrokerRule RULE = new EmbeddedBrokerRule();
  private static String host;
  private static int port;

  @BeforeClass
  public static void setUp() {
    final SocketBindingCfg monitoringApi = RULE.getBrokerCfg().getNetwork().getMonitoringApi();
    host = monitoringApi.getHost();
    port = monitoringApi.getPort();
  }

  @Test
  public void shouldGetMetrics() throws IOException, InterruptedException {
    // when
    final String path = "metrics";
    final var response = sendRequest(host, port, path);

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("jvm_info");
  }

  @Test
  public void shouldGetReadyStatus() throws IOException, InterruptedException {
    // when
    final String path = "ready";
    final var response = sendRequest(host, port, path);
    // then
    assertThat(response.statusCode()).isEqualTo(204);
  }

  @Test
  public void shouldGetHealthStatus() throws IOException, InterruptedException {
    // when
    final String path = "health";
    final var response = sendRequest(host, port, path);
    // then
    assertThat(response.statusCode()).isEqualTo(204);
  }

  private HttpResponse<String> sendRequest(final String host, final int port, final String path)
      throws IOException, InterruptedException {
    final var uri = URI.create(String.format("http://%s:%d/%s", host, port, path));
    final var client = HttpClient.newHttpClient();
    final var request = HttpRequest.newBuilder(uri).build();
    return client.send(request, BodyHandlers.ofString());
  }
}
