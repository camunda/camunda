/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.json.SimpleJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ElasticsearchArchiverRepositoryTest extends AbstractArchiverRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepositoryTest.class);

  private final RestClientTransport transport = Mockito.spy(createRestClient());

  @Test
  void shouldCloseTransportOnClose() throws Exception {
    // when
    repository.close();

    // then
    Mockito.verify(transport, Mockito.times(1)).close();
  }

  @Override
  ElasticsearchArchiverRepository createRepository() {
    final var client = new ElasticsearchAsyncClient(transport);
    final var metrics = new CamundaExporterMetrics(new SimpleMeterRegistry());
    final var config = new HistoryConfiguration();
    config.setRetention(retention);
    return new ElasticsearchArchiverRepository(
        1,
        config,
        new TestExporterResourceProvider("testPrefix", true),
        client,
        Runnable::run,
        metrics,
        LOGGER);
  }

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create("http://127.0.0.1:1")).build();
    return new RestClientTransport(restClient, new SimpleJsonpMapper());
  }
}
