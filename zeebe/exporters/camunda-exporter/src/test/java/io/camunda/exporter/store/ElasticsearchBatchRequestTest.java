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
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import io.camunda.exporter.entities.TestExporterEntity;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ElasticsearchBatchRequestTest {

  private static final String ID = "id";
  private static final String INDEX = "index";
  private ElasticsearchBatchRequest batchRequest;
  private ElasticsearchClient elasticsearchClient;
  private Builder requestBuilder;
  private ElasticsearchScriptBuilder scriptBuilder;

  @BeforeEach
  void setUp() throws IOException {
    elasticsearchClient = mock(ElasticsearchClient.class);
    requestBuilder = new Builder();
    scriptBuilder = mock(ElasticsearchScriptBuilder.class);
    batchRequest =
        new ElasticsearchBatchRequest(elasticsearchClient, requestBuilder, scriptBuilder);
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
  void shouldUpsertWithScript() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = mock(Script.class);
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    // When
    batchRequest.upsertWithScript(INDEX, ID, entity, script, params);
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
    assertThat(update.action().script()).isEqualTo(scriptWithParameters);
    assertThat(update.action().upsert()).isEqualTo(entity);
  }

  @Test
  void shouldUpsertWithScriptAndRouting() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");
    final String routing = "routing";

    final Script scriptWithParameters = mock(Script.class);
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    // When
    batchRequest.upsertWithScriptAndRouting(INDEX, ID, entity, script, params, routing);
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
    assertThat(update.action().script()).isEqualTo(scriptWithParameters);
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
  void shouldUpdateWithScript() throws PersistenceException, IOException {
    // Given
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = mock(Script.class);
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    // When
    batchRequest.updateWithScript(INDEX, ID, script, params);
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
    assertThat(update.action().script()).isEqualTo(scriptWithParameters);
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
}
