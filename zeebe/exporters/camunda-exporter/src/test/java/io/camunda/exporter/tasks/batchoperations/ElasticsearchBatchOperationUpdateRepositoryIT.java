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
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
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

  @BeforeEach
  void beforeEach() {
    Stream.of(batchOperationTemplate, operationTemplate)
        .forEach(template -> engineClient.createIndexTemplate(template, new IndexSettings(), true));
  }

  protected abstract BatchOperationUpdateRepository createRepository();

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

  @Nested
  final class GetFinishedOperationsCountTest {
    @Test
    void shouldReturnEmptyList() {
      // given
      final var repository = createRepository();

      // when
      var operationsAggData = repository.getFinishedOperationsCount(List.of());
      // then
      assertThat(operationsAggData)
          .asInstanceOf(InstanceOfAssertFactories.list(OperationsAggData.class))
          .isEmpty();

      // when
      operationsAggData = repository.getFinishedOperationsCount(null);
      // then
      assertThat(operationsAggData)
          .asInstanceOf(InstanceOfAssertFactories.list(OperationsAggData.class))
          .isEmpty();
    }

    @Test
    void shouldReturnFinishedOperationsCount() throws PersistenceException {
      // given
      final var repository = createRepository();
      // 1, 2, 4, 5 - not finished batch operations, 3 - finished batch operation
      createOperationEntity("111", "1", OperationState.COMPLETED);
      createOperationEntity("222", "1", OperationState.FAILED);
      createOperationEntity("333", "2", OperationState.COMPLETED);
      createOperationEntity("444", "3", OperationState.COMPLETED);
      createOperationEntity("555", "4", OperationState.LOCKED);
      createOperationEntity("666", "5", OperationState.SENT);
      final var expected = List.of(new OperationsAggData("1", 2), new OperationsAggData("2", 1));

      // when
      final var documents = repository.getFinishedOperationsCount(List.of("1", "2", "4", "5"));

      // then
      assertThat(documents)
          .asInstanceOf(InstanceOfAssertFactories.list(OperationsAggData.class))
          .hasSize(2)
          .isEqualTo(expected);
    }

    private OperationEntity createOperationEntity(
        final String id, final String batchOperationId, final OperationState state)
        throws PersistenceException {
      final var operationEntity =
          new OperationEntity().setId(id).setBatchOperationId(batchOperationId).setState(state);
      indexOperation(operationEntity);
      return operationEntity;
    }

    private void indexOperation(final OperationEntity operationEntity) throws PersistenceException {
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
      batchRequest.executeWithRefresh();
    }
  }
}
