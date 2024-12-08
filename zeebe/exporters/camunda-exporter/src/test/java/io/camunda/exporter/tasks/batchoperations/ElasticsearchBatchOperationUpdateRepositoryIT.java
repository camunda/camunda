/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoCloseResources
abstract sealed class ElasticsearchBatchOperationUpdateRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchBatchOperationUpdateRepositoryIT.class);
  protected final BatchOperationTemplate batchOperationTemplate;
  protected final OperationTemplate operationTemplate;
  @AutoCloseResource private final ClientAdapter clientAdapter;
  private final SearchEngineClient engineClient;

  public ElasticsearchBatchOperationUpdateRepositoryIT(
      final String databaseUrl, final boolean isElastic) {
    final var config = new ExporterConfiguration();
    final var indexPrefix = UUID.randomUUID().toString();
    config.getConnect().setIndexPrefix(indexPrefix);
    config.getConnect().setUrl(databaseUrl);
    config.getConnect().setType(isElastic ? "elasticsearch" : "opensearch");

    clientAdapter = ClientAdapter.of(config);
    engineClient = clientAdapter.getSearchEngineClient();

    batchOperationTemplate = new BatchOperationTemplate(indexPrefix, isElastic);
    operationTemplate = new OperationTemplate(indexPrefix, isElastic);
  }

  protected abstract ElasticsearchBatchOperationUpdateRepository createRepository();

  static final class ElasticsearchIT extends ElasticsearchBatchOperationUpdateRepositoryIT {
    @Container
    private static final ElasticsearchContainer CONTAINER =
        TestSearchContainers.createDefeaultElasticsearchContainer();

    @AutoCloseResource private final RestClientTransport transport = createTransport();
    private final ElasticsearchAsyncClient client = new ElasticsearchAsyncClient(transport);

    public ElasticsearchIT() {
      super("http://" + CONTAINER.getHttpHostAddress(), true);
    }

    @Override
    protected ElasticsearchBatchOperationUpdateRepository createRepository() {
      return new ElasticsearchBatchOperationUpdateRepository(
          client,
          Runnable::run,
          batchOperationTemplate.getFullQualifiedName(),
          operationTemplate.getFullQualifiedName(),
          LOGGER);
    }

    private RestClientTransport createTransport() {
      final var restClient =
          RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();
      return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }
  }

  // TODO OpenSearchIT

  @Nested
  final class GetNotFinishedBatchOperationsTest {
    @Test
    void shouldReturnEmptyList() {
      // given
      final var repository = createRepository();

      // when
      final var documents = repository.getNotFinishedBatchOperations();

      // then
      assertThat(documents).asInstanceOf(InstanceOfAssertFactories.list(String.class)).isEmpty();
    }

    @Test
    void shouldReturnBatchOperationIds() throws PersistenceException {
      // given
      final var repository = createRepository();
      createBatchOperationEntity("1", OffsetDateTime.now());
      createBatchOperationEntity("2", OffsetDateTime.now());
      final var expected = createBatchOperationEntity("3", null);

      // when
      final var documents = repository.getNotFinishedBatchOperations();

      // then
      assertThat(documents)
          .asInstanceOf(InstanceOfAssertFactories.list(String.class))
          .hasSize(1)
          .contains(expected.getId());
    }

    private BatchOperationEntity createBatchOperationEntity(
        final String id, final OffsetDateTime endTime) throws PersistenceException {
      final var batchOperationEntity = new BatchOperationEntity().setId(id).setEndDate(endTime);
      indexBatchOperation(batchOperationEntity);
      return batchOperationEntity;
    }

    private void indexBatchOperation(final BatchOperationEntity batchOperationEntity)
        throws PersistenceException {
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(batchOperationTemplate.getFullQualifiedName(), batchOperationEntity);
      batchRequest.executeWithRefresh();
    }
  }
}
