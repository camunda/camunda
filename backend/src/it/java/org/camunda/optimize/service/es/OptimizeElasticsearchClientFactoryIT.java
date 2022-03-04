/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.slf4j.event.Level;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

public class OptimizeElasticsearchClientFactoryIT extends AbstractIT {

  @RegisterExtension
  protected final LogCapturer logCapturer =
    LogCapturer.create().forLevel(Level.ERROR).captureForType(OptimizeElasticsearchClientFactory.class);

  @Test
  @SneakyThrows
  public void testWaitForElasticsearch() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    final HttpRequest elasticHealthRequest = request("/_cluster/health").withMethod(GET);
    // make the connectivity check fail once that is done in OptimizeElasticsearchClientFactory
    esMockServer.when(elasticHealthRequest, Times.once()).error(HttpError.error().withDropConnection(true));

    // when the client is created the factory should retry and wait for a connection to be established
    OptimizeElasticsearchClient optimizeElasticsearchClient = null;
    try {
      optimizeElasticsearchClient = embeddedOptimizeExtension.getApplicationContext()
        .getBean(OptimizeElasticsearchClientConfiguration.class)
        .createOptimizeElasticsearchClient(new BackoffCalculator(1, 1));

      // then
      logCapturer.assertContains("Can't connect to any Elasticsearch node");
      // and the client works
      assertThat(optimizeElasticsearchClient.getHighLevelClient()
        .info(optimizeElasticsearchClient.requestOptions())).isNotNull();
    } finally {
      if (optimizeElasticsearchClient != null) {
        optimizeElasticsearchClient.close();
      }
    }
  }
}
