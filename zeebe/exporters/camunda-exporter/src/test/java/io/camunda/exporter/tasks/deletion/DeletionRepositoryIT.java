/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import static io.camunda.search.test.utils.SearchDBExtension.create;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.DeleterConfiguration;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class DeletionRepositoryIT {
  @RegisterExtension protected static SearchDBExtension searchDB = create();
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  protected final ExporterConfiguration config;
  protected final DeleterConfiguration deleterConfig = new DeleterConfiguration();
  protected final CamundaExporterMetrics metrics =
      new CamundaExporterMetrics(new SimpleMeterRegistry());
  @AutoClose protected final ClientAdapter clientAdapter;
  protected final SearchEngineClient engineClient;
  protected final SearchClientAdapter searchClientAdapter;
  protected final OperationTemplate operationTemplate;

  protected DeletionRepositoryIT(final String databaseUrl, final boolean isElastic) {
    final var indexPrefix =
        "deletion" + RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase();
    config = new ExporterConfiguration();
    config.getConnect().setIndexPrefix(indexPrefix);
    config.getConnect().setUrl(databaseUrl);
    config.getConnect().setType(isElastic ? "elasticsearch" : "opensearch");
    config.getConnect().setAwsEnabled(searchDB.isAws());

    clientAdapter = ClientAdapter.of(config.getConnect());
    engineClient = clientAdapter.getSearchEngineClient();
    searchClientAdapter =
        isElastic
            ? new SearchClientAdapter(searchDB.esClient(), searchDB.objectMapper())
            : new SearchClientAdapter(searchDB.osClient(), searchDB.objectMapper());

    operationTemplate = new OperationTemplate(indexPrefix, isElastic);
  }

  protected abstract DeletionRepository createRepository();

  @BeforeEach
  void beforeEach() {
    engineClient.createIndex(operationTemplate, new IndexConfiguration());
  }

  @AfterEach
  void afterEach() {
    engineClient.deleteIndex(operationTemplate.getFullQualifiedName());
  }

  @Test
  void shouldDeleteOnlyDocumentsMatchingFilter() throws IOException {
    // given
    final var indexName = operationTemplate.getFullQualifiedName();
    final var repository = createRepository();
    indexOperation(indexName, 100L);
    indexOperation(indexName, 100L);
    final var toKeep = indexOperation(indexName, 200L);
    indexOperation(indexName, 300L);
    searchClientAdapter.refresh(indexName);

    final var filter =
        FilterByKeysQuery.of(OperationTemplate.PROCESS_INSTANCE_KEY, List.of("100", "300"));

    // when
    final var result = repository.deleteDocumentsById(indexName, filter, Runnable::run);

    // then
    assertThat(result).succeedsWithin(REQUEST_TIMEOUT);
    searchClientAdapter.refresh(indexName);
    assertThat(searchClientAdapter.searchAll(indexName, OperationEntity.class))
        .extracting(OperationEntity::getId)
        .containsExactly(toKeep.getId());
  }

  private OperationEntity indexOperation(final String indexName, final long processInstanceKey)
      throws IOException {
    final var entity =
        new OperationEntity()
            .setId(UUID.randomUUID().toString())
            .setProcessInstanceKey(processInstanceKey);
    searchClientAdapter.index(entity.getId(), indexName, entity);
    return entity;
  }
}
