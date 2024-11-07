/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration.ArchiverConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.RetentionConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("resource")
@Testcontainers
@AutoCloseResources
final class OpenSearchRepositoryIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchRepositoryIT.class);

  @Container
  private static final OpensearchContainer<?> OPENSEARCH =
      TestSearchContainers.createDefaultOpensearchContainer();

  @AutoCloseResource private final RestClientTransport transport = createRestClient();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ArchiverConfiguration config = new ArchiverConfiguration();
  private final RetentionConfiguration retention = new RetentionConfiguration();
  private final String processInstanceIndex = "process-instance-" + UUID.randomUUID();
  private final String batchOperationIndex = "batch-operation-" + UUID.randomUUID();
  private final OpenSearchClient testClient = new OpenSearchClient(transport);

  @Test
  void shouldDeleteDocuments() throws IOException {
    // given
    final var indexName = UUID.randomUUID().toString();
    final var repository = createRepository();
    final var documents =
        List.of(new TestDocument("1"), new TestDocument("2"), new TestDocument("3"));
    documents.forEach(doc -> index(indexName, doc));
    testClient.indices().refresh(r -> r.index(indexName));

    // when - delete the first two documents
    final var result =
        repository.deleteDocuments(
            indexName, "id", documents.stream().limit(2).map(TestDocument::id).toList());

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(30));
    testClient.indices().refresh(r -> r.index(indexName));
    final var remaining =
        testClient.search(r -> r.index(indexName).requestCache(false), TestDocument.class);
    assertThat(remaining.hits().hits())
        .as("only the third document is remaining")
        .hasSize(1)
        .first()
        .extracting(Hit::id)
        .isEqualTo("3");
  }

  private <T extends TDocument> void index(final String index, final T document) {
    try {
      testClient.index(b -> b.index(index).document(document).id(document.id()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // no need to close resource returned here, since the transport is closed above anyway
  private OpenSearchRepository createRepository() {
    final var client = new OpenSearchAsyncClient(transport);
    final var metrics = new CamundaExporterMetrics(meterRegistry);

    return new OpenSearchRepository(
        1,
        config,
        retention,
        processInstanceIndex,
        batchOperationIndex,
        client,
        Runnable::run,
        metrics,
        LOGGER);
  }

  private RestClientTransport createRestClient() {
    final var restClient =
        RestClient.builder(HttpHost.create(OPENSEARCH.getHttpHostAddress())).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }

  private record TestDocument(String id) implements TDocument {}

  private interface TDocument {
    String id();
  }
}
