/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The number of shards decides the routing values {@link RecordIndexRouter} derives, so reading it
 * back wrong sends documents to a shard other than the one meant for their partition. A stub cannot
 * show that the response is read correctly, hence a real OpenSearch.
 */
@Testcontainers
final class IndexShardCountsIT {

  @Container
  private static final OpenSearchContainer<?> CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  private static OpenSearchClient client;

  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();

  @BeforeAll
  static void beforeAll() {
    final var clientConfig = new OpensearchExporterConfiguration();
    clientConfig.url = CONTAINER.getHttpHostAddress();
    client = OpensearchConnector.of(clientConfig).createClient();
  }

  @AfterAll
  static void afterAll() throws IOException {
    if (client != null) {
      client._transport().close();
    }
  }

  @Test
  void shouldReadTheNumberOfShardsAnIndexWasCreatedWith() throws IOException {
    // given - an index created with more shards than are configured now, as after an operator
    // changed the setting
    final var index = "shard-counts-existing";
    client.indices().create(request -> request.index(index).settings(s -> s.numberOfShards(5)));
    config.index.setNumberOfShards(3);

    // when
    final var numberOfShards = new IndexShardCounts(client, config.index).numberOfShardsOf(index);

    // then - the index's own number, not the configured one, so that routing values stay the ones
    // the index was written with
    assertThat(numberOfShards).isEqualTo(5);
  }

  @Test
  void shouldFallBackToTheConfiguredNumberForAnIndexThatDoesNotExistYet() {
    // given - the exporter is about to create it from its template, which carries this number
    config.index.setNumberOfShards(3);

    // when
    final var numberOfShards =
        new IndexShardCounts(client, config.index).numberOfShardsOf("shard-counts-absent");

    // then
    assertThat(numberOfShards).isEqualTo(3);
  }

  @Test
  void shouldNotResolveANumberWhenNoneIsConfigured() {
    // given - the exporter leaves the number of shards to OpenSearch
    config.index.setNumberOfShards(null);

    // when
    final var numberOfShards =
        new IndexShardCounts(client, config.index).numberOfShardsOf("shard-counts-anything");

    // then - there is nothing to aim a routing value at
    assertThat(numberOfShards).isNull();
  }
}
