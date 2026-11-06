/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;

class DatabaseImportJobExecutorTest {

  @Test
  void shouldNotRetryImportJobInterruptedByShutdown() throws Exception {
    // given
    final var configurationService = mock(ConfigurationService.class);
    when(configurationService.getJobExecutorThreadCount()).thenReturn(1);
    when(configurationService.getJobExecutorQueueSize()).thenReturn(1);
    final var executor = new DatabaseImportJobExecutor("test", configurationService);

    try (final var unresponsiveDatabase = new ServerSocket(0);
        final var client =
            RestClient.builder(new HttpHost("localhost", unresponsiveDatabase.getLocalPort()))
                .build()) {
      final var job = new SearchingImportJob(client);
      job.setEntitiesToImport(List.of(mock(OptimizeDto.class)));
      executor.executeImportJob(job);

      try (final var inFlightRequest = unresponsiveDatabase.accept()) {
        // when
        executor.stopExecutingImportJobs();
      }

      // then
      assertThat(job.attempts).hasValue(1);
    }
  }

  private static final class SearchingImportJob extends DatabaseImportJob<OptimizeDto> {

    private static final int MAX_ATTEMPTS = 1_000;

    private final RestClient client;
    private final AtomicInteger attempts = new AtomicInteger();

    private SearchingImportJob(final RestClient client) {
      super(() -> {}, mock(DatabaseClient.class), ValueType.VARIABLE.name(), 1);
      this.client = client;
    }

    @Override
    protected void persistEntities(final List<OptimizeDto> newOptimizeEntities) throws Exception {
      if (attempts.incrementAndGet() < MAX_ATTEMPTS) {
        client.performRequest(new Request("GET", "/_search"));
      }
    }
  }
}
