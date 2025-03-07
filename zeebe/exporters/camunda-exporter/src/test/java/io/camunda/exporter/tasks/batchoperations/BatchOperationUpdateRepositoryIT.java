/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static io.camunda.exporter.utils.SearchDBExtension.*;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.db.se.config.IndexSettings;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import io.camunda.exporter.utils.SearchDBExtension;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class BatchOperationUpdateRepositoryIT {
  @RegisterExtension protected static SearchDBExtension searchDB = create();
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationUpdateRepositoryIT.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  protected final BatchOperationTemplate batchOperationTemplate;
  protected final OperationTemplate operationTemplate;
  @AutoClose protected final ClientAdapter clientAdapter;
  protected final SearchEngineClient engineClient;
  protected final ExporterConfiguration config;

  public BatchOperationUpdateRepositoryIT(final String databaseUrl, final boolean isElastic) {
    config = new ExporterConfiguration();
    final var indexPrefix = BATCH_IDX_PREFIX + UUID.randomUUID();
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
        .forEach(
            template -> {
              engineClient.createIndexTemplate(template, new IndexSettings(), true);
              engineClient.createIndex(template, new IndexSettings());
            });
  }

  protected abstract BatchOperationUpdateRepository createRepository();

  protected void indexBatchOperation(final BatchOperationEntity batchOperationEntity)
      throws PersistenceException {
    final var batchRequest = clientAdapter.createBatchRequest();
    batchRequest.add(batchOperationTemplate.getFullQualifiedName(), batchOperationEntity);
    batchRequest.executeWithRefresh();
  }

  protected abstract BatchOperationEntity getBatchOperationEntity(final String id);

  static final class ElasticsearchIT extends BatchOperationUpdateRepositoryIT {

    @AutoClose private final RestClientTransport transport = createTransport();
    private final ElasticsearchAsyncClient client;

    public ElasticsearchIT() {
      super("http://" + searchDB.esUrl(), true);
      final var connector = new ElasticsearchConnector(config.getConnect());
      client = connector.createAsyncClient();
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

    @Override
    protected void indexBatchOperation(final BatchOperationEntity batchOperationEntity)
        throws PersistenceException {
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(batchOperationTemplate.getFullQualifiedName(), batchOperationEntity);
      batchRequest.executeWithRefresh();
    }

    @Override
    protected BatchOperationEntity getBatchOperationEntity(final String id) {
      try {
        return client
            .get(
                r -> r.index(batchOperationTemplate.getFullQualifiedName()).id(id),
                BatchOperationEntity.class)
            .get()
            .source();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } catch (final ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    private RestClientTransport createTransport() {
      final var restClient = RestClient.builder(HttpHost.create(searchDB.esUrl())).build();
      return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }
  }

  @Nested
  @Order(1)
  final class GetNotFinishedBatchOperationsTest {
    @Test
    void shouldReturnEmptyList() {
      // given
      final var repository = createRepository();

      // when
      final var documents = repository.getNotFinishedBatchOperations();

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(String.class))
          .isEmpty();
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
          .succeedsWithin(REQUEST_TIMEOUT)
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
  }

  @Nested
  @Order(2)
  final class GetFinishedOperationsCountTest {
    @Test
    void shouldReturnEmptyList() {
      // given
      final var repository = createRepository();

      // when
      var operationsAggData = repository.getFinishedOperationsCount(List.of());
      // then
      assertThat(operationsAggData)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(OperationsAggData.class))
          .isEmpty();

      // when
      operationsAggData = repository.getFinishedOperationsCount(null);
      // then
      assertThat(operationsAggData)
          .succeedsWithin(REQUEST_TIMEOUT)
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
          .succeedsWithin(REQUEST_TIMEOUT)
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

  @Nested
  @Order(3)
  final class BulkUpdateTest {

    @Test
    public void shouldUpdateBatchOperations() throws PersistenceException {
      // given
      final var repository = createRepository();
      createBatchOperationEntity("1", 2);
      createBatchOperationEntity("2", 100);
      createBatchOperationEntity("3", 55);

      // when
      final var updated =
          repository.bulkUpdate(List.of(new DocumentUpdate("1", 2), new DocumentUpdate("2", 50)));

      // then
      assertThat(updated)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
          .isEqualTo(2);

      final BatchOperationEntity batchOperationEntity1 = getBatchOperationEntity("1");
      assertThat(batchOperationEntity1.getEndDate()).isNotNull();
      assertThat(batchOperationEntity1.getOperationsFinishedCount())
          .isEqualTo(batchOperationEntity1.getOperationsTotalCount())
          .isEqualTo(2);

      final BatchOperationEntity batchOperationEntity2 = getBatchOperationEntity("2");
      assertThat(batchOperationEntity2.getEndDate()).isNull();
      assertThat(batchOperationEntity2.getOperationsFinishedCount()).isEqualTo(50);

      final BatchOperationEntity batchOperationEntity3 = getBatchOperationEntity("3");
      assertThat(batchOperationEntity3.getEndDate()).isNull();
      assertThat(batchOperationEntity3.getOperationsFinishedCount()).isEqualTo(0);
    }

    private BatchOperationEntity createBatchOperationEntity(
        final String id, final int operationsTotalCount) throws PersistenceException {
      final var batchOperationEntity =
          new BatchOperationEntity().setId(id).setOperationsTotalCount(operationsTotalCount);
      indexBatchOperation(batchOperationEntity);
      return batchOperationEntity;
    }
  }
}
