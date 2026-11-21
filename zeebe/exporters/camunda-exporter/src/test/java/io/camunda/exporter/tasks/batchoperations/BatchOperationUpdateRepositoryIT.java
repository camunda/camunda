/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import static io.camunda.search.test.utils.SearchDBExtension.*;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.NotFinishedBatchOperation;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
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
  private static final int ORDINAL = 1001;
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
    config.getConnect().setAwsEnabled(searchDB.isAws());
    config.getConnect().setSocketTimeout(60_000);

    clientAdapter = ClientAdapter.of(config.getConnect());
    engineClient = clientAdapter.getSearchEngineClient();

    batchOperationTemplate = new BatchOperationTemplate(indexPrefix, isElastic);
    operationTemplate = new OperationTemplate(indexPrefix, isElastic);
  }

  @BeforeEach
  void beforeEach() {
    Stream.of(batchOperationTemplate, operationTemplate)
        .forEach(
            template -> {
              engineClient.createIndexTemplate(template, new IndexConfiguration(), true);
              engineClient.createIndex(template, new IndexConfiguration());
            });
  }

  protected abstract BatchOperationUpdateRepository createRepository();

  protected void indexBatchOperation(final BatchOperationEntity batchOperationEntity)
      throws PersistenceException {
    indexBatchOperation(mainIndex(batchOperationTemplate), batchOperationEntity);
  }

  protected void indexBatchOperation(
      final TargetIndex index, final BatchOperationEntity batchOperationEntity)
      throws PersistenceException {
    final var batchRequest = clientAdapter.createBatchRequest();
    batchRequest.add(index, batchOperationEntity);
    batchRequest.executeWithRefresh();
  }

  protected BatchOperationEntity getBatchOperationEntity(final String id) {
    return getBatchOperationEntity(batchOperationTemplate.getFullQualifiedName(), id);
  }

  protected abstract BatchOperationEntity getBatchOperationEntity(
      final String index, final String id);

  private static TargetIndex mainIndex(final IndexTemplateDescriptor template) {
    return TargetIndex.mainIndex(template.getFullQualifiedName());
  }

  private static TargetIndex ordinalIndex(final IndexTemplateDescriptor template) {
    return TargetIndex.ordinalIndex(template.getFullQualifiedName(), ORDINAL);
  }

  /** A dated index as the archiver creates it, which holds historical data only. */
  private static TargetIndex datedIndex(final IndexTemplateDescriptor template) {
    return TargetIndex.mainIndex(template.getFullQualifiedName() + "2026-11-16");
  }

  static final class ElasticsearchIT extends BatchOperationUpdateRepositoryIT {

    @AutoClose private final ElasticsearchAsyncClient client;

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
          batchOperationTemplate.getActiveNames(),
          operationTemplate.getActiveNames(),
          LOGGER);
    }

    @Override
    protected void indexBatchOperation(
        final TargetIndex index, final BatchOperationEntity batchOperationEntity)
        throws PersistenceException {
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(index, batchOperationEntity);
      Awaitility.await()
          .ignoreException(PersistenceException.class)
          .timeout(searchDB.dataAvailabilityTimeout())
          .until(
              () -> {
                batchRequest.executeWithRefresh();
                return true;
              });
    }

    @Override
    protected BatchOperationEntity getBatchOperationEntity(final String index, final String id) {
      try {
        return client.get(r -> r.index(index).id(id), BatchOperationEntity.class).get().source();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } catch (final ExecutionException e) {
        throw new RuntimeException(e);
      }
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
      final var documents = repository.getNotFinishedBatchOperations(100, null);

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(NotFinishedBatchOperation.class))
          .isEmpty();
    }

    @Test
    void shouldReturnBatchOperationsWithStateAndTotalCount() throws PersistenceException {
      // given
      final var repository = createRepository();
      createBatchOperationEntity("1", OffsetDateTime.now(), BatchOperationState.COMPLETED, 2);
      createBatchOperationEntity("2", OffsetDateTime.now(), BatchOperationState.COMPLETED, 2);
      createBatchOperationEntity("3", null, BatchOperationState.ACTIVE, 5);

      // when
      final var documents = repository.getNotFinishedBatchOperations(100, null);

      // then the update task can tell how many items the batch operation has, and whether it is
      // finished, without a second request
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(NotFinishedBatchOperation.class))
          .containsExactly(
              new NotFinishedBatchOperation(
                  "3",
                  batchOperationTemplate.getFullQualifiedName(),
                  BatchOperationState.ACTIVE,
                  5));
    }

    @Test
    void shouldReturnBatchOperationWithoutItems() throws PersistenceException {
      // given a completed batch operation whose item query matched nothing
      final var repository = createRepository();
      createBatchOperationEntity("1", null, BatchOperationState.COMPLETED, 0);

      // when
      final var documents = repository.getNotFinishedBatchOperations(100, null);

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(NotFinishedBatchOperation.class))
          .containsExactly(
              new NotFinishedBatchOperation(
                  "1",
                  batchOperationTemplate.getFullQualifiedName(),
                  BatchOperationState.COMPLETED,
                  0));
    }

    @Test
    void shouldReturnNoMoreThanTheRequestedBatchSize() throws PersistenceException {
      // given - more unfinished batch operations than a cycle is allowed to read. Unbounded, every
      // one of them went into a single operations-count aggregation and a single bulk update, which
      // is how a support cluster built a request the circuit breaker refused.
      final var repository = createRepository();
      for (int i = 0; i < 25; i++) {
        createBatchOperationEntity(String.valueOf(i), null, BatchOperationState.ACTIVE, 5);
      }

      // when
      final var documents = repository.getNotFinishedBatchOperations(10, null);

      // then - the read is capped, and with it both requests derived from what it returns
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(NotFinishedBatchOperation.class))
          .hasSize(10);
    }

    @Test
    void shouldReturnTheNextPageAfterTheGivenId() throws PersistenceException {
      // given - three unfinished operations, indexed out of id order
      final var repository = createRepository();
      createBatchOperationEntity("c", null, BatchOperationState.ACTIVE, 5);
      createBatchOperationEntity("a", null, BatchOperationState.ACTIVE, 5);
      createBatchOperationEntity("b", null, BatchOperationState.ACTIVE, 5);

      // when - read in pages of two
      final var firstPage = repository.getNotFinishedBatchOperations(2, null);
      final var nextPage = repository.getNotFinishedBatchOperations(2, "b");

      // then - pages follow the id order, so paging on the last id reaches every operation once
      assertThat(firstPage)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(NotFinishedBatchOperation.class))
          .extracting(NotFinishedBatchOperation::id)
          .containsExactly("a", "b");
      assertThat(nextPage)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(NotFinishedBatchOperation.class))
          .extracting(NotFinishedBatchOperation::id)
          .containsExactly("c");
    }

    @Test
    void shouldReturnBatchOperationsFromMainAndOrdinalIndicesOnly() throws PersistenceException {
      // given - unfinished batch operations in the main index, an ordinal index and a dated
      // (historical) index
      final var repository = createRepository();
      createBatchOperationEntity(
          mainIndex(batchOperationTemplate), "1", null, BatchOperationState.ACTIVE, 5);
      createBatchOperationEntity(
          ordinalIndex(batchOperationTemplate), "2", null, BatchOperationState.ACTIVE, 6);
      createBatchOperationEntity(
          datedIndex(batchOperationTemplate), "3", null, BatchOperationState.ACTIVE, 7);

      // when
      final var documents = repository.getNotFinishedBatchOperations(100, null);

      // then - the active indices are read, each batch operation carries the index it lives in,
      // and the dated index is not touched
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(NotFinishedBatchOperation.class))
          .containsExactly(
              new NotFinishedBatchOperation(
                  "1", mainIndex(batchOperationTemplate).name(), BatchOperationState.ACTIVE, 5),
              new NotFinishedBatchOperation(
                  "2", ordinalIndex(batchOperationTemplate).name(), BatchOperationState.ACTIVE, 6));
    }

    private void createBatchOperationEntity(
        final String id,
        final OffsetDateTime endTime,
        final BatchOperationState state,
        final int operationsTotalCount)
        throws PersistenceException {
      createBatchOperationEntity(
          mainIndex(batchOperationTemplate), id, endTime, state, operationsTotalCount);
    }

    private void createBatchOperationEntity(
        final TargetIndex index,
        final String id,
        final OffsetDateTime endTime,
        final BatchOperationState state,
        final int operationsTotalCount)
        throws PersistenceException {
      indexBatchOperation(
          index,
          new BatchOperationEntity()
              .setId(id)
              .setEndDate(endTime)
              .setState(state)
              .setOperationsTotalCount(operationsTotalCount));
    }
  }

  @Nested
  @Order(2)
  final class GetOperationsCountTest {
    @Test
    void shouldReturnEmptyList() {
      // given
      final var repository = createRepository();

      // when
      var operationsAggData = repository.getOperationsCount(List.of());
      // then
      assertThat(operationsAggData)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(OperationsAggData.class))
          .isEmpty();

      // when
      operationsAggData = repository.getOperationsCount(null);
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
      createOperationEntity("111a", "1", OperationState.COMPLETED);
      createOperationEntity("111b", "1", OperationState.FAILED);
      createOperationEntity("222a", "2", OperationState.COMPLETED);
      createOperationEntity("222b", "2", OperationState.SKIPPED);
      createOperationEntity("333a", "3", OperationState.COMPLETED);
      createOperationEntity("444a", "4", OperationState.LOCKED);
      createOperationEntity("555a", "5", OperationState.SENT);

      final var expected =
          List.of(
              new OperationsAggData("1", Map.of("COMPLETED", 1L, "FAILED", 1L)),
              new OperationsAggData("2", Map.of("COMPLETED", 1L, "SKIPPED", 1L)),
              new OperationsAggData("4", Map.of("LOCKED", 1L)),
              new OperationsAggData("5", Map.of("SENT", 1L)));

      // when
      final var documents = repository.getOperationsCount(List.of("1", "2", "4", "5"));

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(OperationsAggData.class))
          .hasSize(4)
          .isEqualTo(expected);
    }

    @Test
    void shouldCountOperationsFromMainAndOrdinalIndicesOnly() throws PersistenceException {
      // given - the single operations of one batch operation are spread over the main index and an
      // ordinal index, with a stray one in a dated (historical) index
      final var repository = createRepository();
      createOperationEntity(mainIndex(operationTemplate), "111a", "1", OperationState.COMPLETED);
      createOperationEntity(ordinalIndex(operationTemplate), "111b", "1", OperationState.FAILED);
      createOperationEntity(ordinalIndex(operationTemplate), "111c", "1", OperationState.COMPLETED);
      createOperationEntity(datedIndex(operationTemplate), "111d", "1", OperationState.COMPLETED);

      // when
      final var documents = repository.getOperationsCount(List.of("1"));

      // then - operations in both active indices are counted, the dated one is not
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(OperationsAggData.class))
          .containsExactly(new OperationsAggData("1", Map.of("COMPLETED", 2L, "FAILED", 1L)));
    }

    private OperationEntity createOperationEntity(
        final String id, final String batchOperationKey, final OperationState state)
        throws PersistenceException {
      return createOperationEntity(mainIndex(operationTemplate), id, batchOperationKey, state);
    }

    private OperationEntity createOperationEntity(
        final TargetIndex index,
        final String id,
        final String batchOperationKey,
        final OperationState state)
        throws PersistenceException {
      final var operationEntity =
          new OperationEntity().setId(id).setBatchOperationId(batchOperationKey).setState(state);
      indexOperation(index, operationEntity);
      return operationEntity;
    }

    private void indexOperation(final TargetIndex index, final OperationEntity operationEntity)
        throws PersistenceException {
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(index, operationEntity);
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
      createBatchOperationEntity("1", 2, BatchOperationState.COMPLETED);
      createBatchOperationEntity("2", 2, BatchOperationState.ACTIVE);
      createBatchOperationEntity("3", 100, BatchOperationState.ACTIVE);
      createBatchOperationEntity("4", 55, BatchOperationState.ACTIVE);

      // when
      final var updated =
          repository.bulkUpdate(
              List.of(
                  new DocumentUpdate(
                      "1", batchOperationTemplate.getFullQualifiedName(), 2, 0, 2, 2),
                  new DocumentUpdate(
                      "2", batchOperationTemplate.getFullQualifiedName(), 2, 0, 2, 2),
                  new DocumentUpdate(
                      "3", batchOperationTemplate.getFullQualifiedName(), 50, 0, 50, 50)));

      // then
      assertThat(updated)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
          .isEqualTo(3);

      final BatchOperationEntity batchOperationEntity1 = getBatchOperationEntity("1");
      assertThat(batchOperationEntity1.getEndDate()).isNotNull();
      assertThat(batchOperationEntity1.getOperationsFinishedCount())
          .isEqualTo(batchOperationEntity1.getOperationsTotalCount())
          .isEqualTo(2);

      final BatchOperationEntity batchOperationEntity2 = getBatchOperationEntity("2");
      assertThat(batchOperationEntity2.getEndDate()).isNull();
      assertThat(batchOperationEntity2.getOperationsFinishedCount())
          .isEqualTo(batchOperationEntity2.getOperationsTotalCount())
          .isEqualTo(2);

      final BatchOperationEntity batchOperationEntity3 = getBatchOperationEntity("3");
      assertThat(batchOperationEntity3.getEndDate()).isNull();
      assertThat(batchOperationEntity3.getOperationsFinishedCount()).isEqualTo(50);

      final BatchOperationEntity batchOperationEntity4 = getBatchOperationEntity("4");
      assertThat(batchOperationEntity4.getEndDate()).isNull();
      assertThat(batchOperationEntity4.getOperationsFinishedCount()).isEqualTo(0);
    }

    @Test
    public void shouldSetEndDateOfCompletedBatchOperationWithoutOperations()
        throws PersistenceException {
      // given a completed batch operation whose item query matched nothing, so it has no items
      final var repository = createRepository();
      createBatchOperationEntity("1", 0, BatchOperationState.COMPLETED);

      // when
      final var updated =
          repository.bulkUpdate(
              List.of(
                  new DocumentUpdate(
                      "1", batchOperationTemplate.getFullQualifiedName(), 0, 0, 0, 0)));

      // then
      assertThat(updated)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
          .isEqualTo(1);

      final BatchOperationEntity batchOperationEntity = getBatchOperationEntity("1");
      assertThat(batchOperationEntity.getEndDate()).isNotNull();
      assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(0);
    }

    @Test
    public void shouldUpdateBatchOperationInTheGivenOrdinalIndex() throws PersistenceException {
      // given - a completed batch operation stored in an ordinal index
      final var repository = createRepository();
      final var ordinalIndex = ordinalIndex(batchOperationTemplate);
      indexBatchOperation(
          ordinalIndex,
          new BatchOperationEntity()
              .setId("1")
              .setOperationsTotalCount(2)
              .setState(BatchOperationState.COMPLETED));

      // when
      final var updated =
          repository.bulkUpdate(List.of(new DocumentUpdate("1", ordinalIndex.name(), 2, 0, 2, 2)));

      // then - the document in the ordinal index is updated, and none is created in the main one
      assertThat(updated)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.type(Integer.class))
          .isEqualTo(1);
      final var batchOperationEntity = getBatchOperationEntity(ordinalIndex.name(), "1");
      assertThat(batchOperationEntity.getEndDate()).isNotNull();
      assertThat(batchOperationEntity.getOperationsFinishedCount()).isEqualTo(2);
      assertThat(getBatchOperationEntity("1")).isNull();
    }

    private BatchOperationEntity createBatchOperationEntity(
        final String id, final int operationsTotalCount, final BatchOperationState state)
        throws PersistenceException {
      final var batchOperationEntity =
          new BatchOperationEntity()
              .setId(id)
              .setOperationsTotalCount(operationsTotalCount)
              .setState(state);
      indexBatchOperation(batchOperationEntity);
      return batchOperationEntity;
    }
  }
}
