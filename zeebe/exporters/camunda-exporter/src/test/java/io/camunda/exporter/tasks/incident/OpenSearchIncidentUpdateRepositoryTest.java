/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
public final class OpenSearchIncidentUpdateRepositoryTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchIncidentUpdateRepositoryTest.class);

  @Mock private OpenSearchAsyncClient client;

  @RegressionTest("https://github.com/camunda/camunda/pull/53585")
  void shouldSkipBulkCallWhenUpdateIsEmpty() throws Exception {
    // given
    final var repository = createRepository();

    // when
    final var result = repository.bulkUpdate(new IncidentBulkUpdate());

    // then - client.bulk() must not be invoked; previously this sent an empty body and OS threw
    // "[os/bulk] failed: [parse_exception] request body is required"
    assertThat(result).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(List.of());
    Mockito.verify(client, Mockito.never()).bulk(Mockito.any(BulkRequest.class));
  }

  private OpenSearchIncidentUpdateRepository createRepository() {
    return new OpenSearchIncidentUpdateRepository(
        1,
        "pendingUpdateAlias",
        "incidentAlias",
        "listViewAlias",
        "listViewFullQualifiedName",
        "flowNodeAlias",
        "operationAlias",
        client,
        Runnable::run,
        LOGGER);
  }
}
