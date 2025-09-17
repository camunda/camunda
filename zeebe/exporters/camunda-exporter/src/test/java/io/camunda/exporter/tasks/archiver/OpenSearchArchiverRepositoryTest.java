/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenSearchArchiverRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchArchiverRepositoryTest.class);

  private final RestClientTransport transport = Mockito.spy(createRestClient());
  private final RetentionConfiguration retention = new RetentionConfiguration();

  @Test
  void shouldCloseTransportOnClose() throws Exception {
    // given
    final var repository = createRepository();

    // when
    repository.close();

    // then
    Mockito.verify(transport, Mockito.times(1)).close();
  }

  @Test
  void shouldNotSetLifecycleIfRetentionIsDisabled() {
    // given
    final var repository = createRepository();
    retention.setEnabled(false);

    // when
    final var result = repository.setIndexLifeCycle("whatever");

    // then - would normally fail if tried to access ES, since there is no backing Elastic
    assertThat(result)
        .as("did not try connecting to non existent ES")
        .succeedsWithin(Duration.ofSeconds(5));
  }

  private OpenSearchArchiverRepository createRepository() {
    final var client = new OpenSearchAsyncClient(transport);
    final var metrics = new CamundaExporterMetrics(new SimpleMeterRegistry());
    final var config = new HistoryConfiguration();
    config.setRetention(retention);

    return new OpenSearchArchiverRepository(
        1,
        config,
        new TestExporterResourceProvider("testPrefix", false),
        client,
        new OpenSearchGenericClient(client._transport(), client._transportOptions()),
        Runnable::run,
        metrics,
        LOGGER);
  }

  private RestClientTransport createRestClient() {
    final var restClient = RestClient.builder(HttpHost.create("http://127.0.0.1:1")).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }
}
