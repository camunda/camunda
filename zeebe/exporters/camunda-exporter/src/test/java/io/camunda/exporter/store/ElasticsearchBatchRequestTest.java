/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import io.camunda.exporter.entities.TestExporterEntity;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ElasticsearchBatchRequestTest {

  private static final String ID = "id";
  private static final String INDEX = "index";
  private static final String INDEX_WITH_HANDLER = "indexWithHandler";
  private ElasticsearchBatchRequest batchRequest;
  private ElasticsearchClient elasticsearchClient;
  private Builder requestBuilder;

  @BeforeEach
  void setUp() throws IOException {
    elasticsearchClient = mock(ElasticsearchClient.class);
    requestBuilder = new Builder();
    batchRequest = new ElasticsearchBatchRequest(elasticsearchClient, requestBuilder);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
  }

  @Test
  void shouldAddABulkOperationWithSpecifiedIndexAndEntity()
      throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // When
    batchRequest.add(INDEX, entity);
    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isIndex()).isTrue();
    final IndexOperation<TestExporterEntity> index = bulkOperation.index();
    assertThat(index.index()).isEqualTo(INDEX);
    assertThat(index.id()).isEqualTo(ID);
    assertThat(index.document()).isEqualTo(entity);
  }

  @Test
  void shouldAddWithRouting() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String routing = "routing";

    // When
    batchRequest.addWithRouting(INDEX, entity, routing);

    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isIndex()).isTrue();

    final IndexOperation<TestExporterEntity> index = bulkOperation.index();
    assertThat(index.index()).isEqualTo(INDEX);
    assertThat(index.id()).isEqualTo(ID);
    assertThat(index.routing()).isEqualTo(routing);
    assertThat(index.document()).isEqualTo(entity);
  }

  @Test
  void shouldUpsertEntityWithUpdatedFields() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final Map<String, Object> updateFields = Map.of("id", "id2");

    // When
    batchRequest.upsert(INDEX, ID, entity, updateFields);
    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().doc()).isEqualTo(updateFields);
    assertThat(update.action().upsert()).isEqualTo(entity);
  }

  @Test
  void shouldUpsertWithRouting() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final Map<String, Object> updateFields = Map.of("id", "id2");
    final String routing = "routing";

    // When
    batchRequest.upsertWithRouting(INDEX, ID, entity, updateFields, routing);
    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.routing()).isEqualTo(routing);
    assertThat(update.action().doc()).isEqualTo(updateFields);
    assertThat(update.action().upsert()).isEqualTo(entity);
  }

  @Test
  void shouldUpdateWithFields() throws PersistenceException, IOException {
    final Map<String, Object> updateFields = Map.of("id", "id2");

    // When
    batchRequest.update(INDEX, ID, updateFields);
    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().doc()).isEqualTo(updateFields);
  }

  @Test
  void shouldUpdateWithEntity() throws IOException, PersistenceException {
    // Given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // When
    batchRequest.update(INDEX, ID, entity);
    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().doc()).isEqualTo(entity);
  }

  @Test
  void shouldDeleteEntity() throws IOException, PersistenceException {
    // When
    batchRequest.delete(INDEX, ID);
    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isDelete()).isTrue();

    final var delete = bulkOperation.delete();
    assertThat(delete.index()).isEqualTo(INDEX);
    assertThat(delete.id()).isEqualTo(ID);
  }

  @Test
  void shouldDeleteEntityWithRouting() throws IOException, PersistenceException {
    // given
    final String routing = "routing";

    // when
    batchRequest.deleteWithRouting(INDEX, ID, routing);
    batchRequest.execute();

    // then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that an index operation is added
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);

    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isDelete()).isTrue();

    final var delete = bulkOperation.delete();
    assertThat(delete.index()).isEqualTo(INDEX);
    assertThat(delete.id()).isEqualTo(ID);
    assertThat(delete.routing()).isEqualTo(routing);
  }

  @Test
  void shouldExecuteWithMultipleOperationsInBatch() throws PersistenceException, IOException {
    // Given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    // When
    batchRequest.add(INDEX, entity);
    batchRequest.update(INDEX, ID, entity);
    batchRequest.execute();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());

    // verify that there are two operations in the bulk request
    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(2);
  }

  @Test
  void shouldExecuteWithRefresh() throws PersistenceException, IOException {
    // Given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // When
    batchRequest.add(INDEX, entity);
    batchRequest.executeWithRefresh();

    // Then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient).bulk(captor.capture());
    final BulkRequest request = captor.getValue();
    assertThat(request.refresh()).isEqualTo(Refresh.True);
  }

  @ParameterizedTest
  @ValueSource(classes = {IOException.class, ElasticsearchException.class})
  void shouldThrowPersistenceExceptionIfBulkRequestFails(final Class<? extends Throwable> throwable)
      throws IOException {
    // Given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // When
    batchRequest.add(INDEX, entity);
    when(elasticsearchClient.bulk(any(BulkRequest.class))).thenThrow(throwable);

    // When
    final ThrowingCallable callable = () -> batchRequest.execute();

    // Then
    assertThatThrownBy(callable).isInstanceOf(PersistenceException.class);
    verify(elasticsearchClient).bulk(any(BulkRequest.class));
  }

  @Test
  void shouldThrowPersistenceExceptionIfAResponseItemHasError() throws IOException {
    // Given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    final BulkResponseItem item = mock(BulkResponseItem.class);
    when(item.error()).thenReturn(new ErrorCause.Builder().reason("error").build());

    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(List.of(item));

    when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

    // When
    batchRequest.add(INDEX, entity);
    final ThrowingCallable callable = () -> batchRequest.execute();

    // Then
    assertThatThrownBy(callable).isInstanceOf(PersistenceException.class);
    verify(elasticsearchClient).bulk(any(BulkRequest.class));
  }

  @Test
  void shouldUseCustomErrorHandlerIfProvided() throws IOException {
    // Given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final OperationType operationType = OperationType.Update;
    final int notFound = 404;

    final BulkResponseItem item = mock(BulkResponseItem.class);
    when(item.id()).thenReturn(ID);
    when(item.operationType()).thenReturn(operationType);
    when(item.index()).thenReturn(INDEX_WITH_HANDLER);
    when(item.status()).thenReturn(notFound);
    when(item.error())
        .thenReturn(
            new ErrorCause.Builder().reason("error").type("document_missing_exception").build());

    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(List.of(item));

    when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
    final BiConsumer<String, Error> errorHandler = mock(BiConsumer.class);

    final String message =
        String.format(
            "%s failed for type [%s] and id [%s]: %s",
            item.operationType(), item.index(), item.id(), item.error().reason());

    // When
    batchRequest.add(INDEX_WITH_HANDLER, entity);
    batchRequest.execute(errorHandler);

    // Then
    verify(errorHandler)
        .accept(INDEX_WITH_HANDLER, new Error(message, item.error().type(), notFound));
  }
}
