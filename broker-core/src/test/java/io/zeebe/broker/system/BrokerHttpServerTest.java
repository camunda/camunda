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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class BrokerHttpServerTest {

  @ClassRule public static final EmbeddedBrokerRule RULE = new EmbeddedBrokerRule();

  private static String baseUrl;

  @BeforeClass
  public static void setUp() {
    final SocketBindingCfg monitoringApi = RULE.getBrokerCfg().getNetwork().getMonitoringApi();
    baseUrl = String.format("http://%s:%d", monitoringApi.getHost(), monitoringApi.getPort());
  }

  @Test
  public void shouldGetMetrics() throws IOException {
    final String url = baseUrl + "/metrics";

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final HttpGet request = new HttpGet(url);
      try (CloseableHttpResponse response = client.execute(request)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(EntityUtils.toString(response.getEntity())).contains("jvm_info");
      }
    }
  }

  @Test
  public void shouldGetReadyStatus() throws IOException {
    final String url = baseUrl + "/ready";

    try (CloseableHttpClient client = HttpClients.createDefault()) {
      final HttpGet request = new HttpGet(url);
      try (CloseableHttpResponse response = client.execute(request)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(204);
      }
    }
  }
}
