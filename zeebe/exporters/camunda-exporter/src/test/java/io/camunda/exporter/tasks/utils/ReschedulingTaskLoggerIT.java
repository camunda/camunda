/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.utils;

import static org.awaitility.Awaitility.await;

import co.elastic.clients.json.JsonData;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.tasks.util.ReschedulingTaskLogger;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ReschedulingTaskLoggerIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @Test
  void shouldSuppressAllShardsFailedTransientError() {
    // given
    final var config = new ExporterConfiguration();
    config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    final var client = new ElasticsearchConnector(config.getConnect()).createAsyncClient();

    // this will result in newly created indices not being assigned to shards, so an all-shards
    // failed error is thrown for a search request
    client
        .cluster()
        .putSettings(r -> r.persistent("cluster.routing.allocation.enable", JsonData.of("none")));

    client.indices().create(r -> r.index("test_index"));

    await().until(() -> client.indices().exists(r -> r.index("test_index")).get().value());

    // when
    final var res = client.search(r -> r.index("test_index"), Object.class);

    final var logger = Mockito.spy(LoggerFactory.getLogger(ReschedulingTaskLoggerIT.class));
    final var reschedulingTaskLogger = new ReschedulingTaskLogger(logger, true);

    final AtomicReference<Throwable> searchErr = new AtomicReference<>();
    res.exceptionally(
        ex -> {
          searchErr.set(ex);
          return null;
        });

    reschedulingTaskLogger.logError("", searchErr.get());

    // then
    Mockito.verify(logger).atLevel(Level.WARN);
    Mockito.verify(logger, Mockito.times(0)).atLevel(Level.ERROR);
  }
}
