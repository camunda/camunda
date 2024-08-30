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
  void setUp() {
    elasticsearchClient = mock(ElasticsearchClient.class);
    requestBuilder = mock(Builder.class);
    scriptBuilder = mock(ElasticsearchScriptBuilder.class);
    batchRequest =
        new ElasticsearchBatchRequest(elasticsearchClient, requestBuilder, scriptBuilder);
  }

  @Test
  void shouldAddABulkOperationWithSpecifiedIndexAndEntity() throws PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // When
    batchRequest.add(INDEX, entity);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isIndex()).isTrue();

    final IndexOperation<TestExporterEntity> index = bulkOperation.index();
    assertThat(index.index()).isEqualTo(INDEX);
    assertThat(index.id()).isEqualTo(ID);
    assertThat(index.document()).isEqualTo(entity);
  }

  @Test
  void shouldAddWithRouting() throws PersistenceException {

    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String routing = "routing";

    // When
    batchRequest.addWithRouting(INDEX, entity, routing);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isIndex()).isTrue();

    final IndexOperation<TestExporterEntity> index = bulkOperation.index();
    assertThat(index.index()).isEqualTo(INDEX);
    assertThat(index.id()).isEqualTo(ID);
    assertThat(index.routing()).isEqualTo(routing);
    assertThat(index.document()).isEqualTo(entity);
  }

  @Test
  void shouldUpsertEntityWithUpdatedFields() throws PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final Map<String, Object> updateFields = Map.of("id", "id2");

    // When
    batchRequest.upsert(INDEX, ID, entity, updateFields);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().doc()).isEqualTo(updateFields);
    assertThat(update.action().upsert()).isEqualTo(entity);
  }

  @Test
  void shouldUpsertWithRouting() throws PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final Map<String, Object> updateFields = Map.of("id", "id2");
    final String routing = "routing";

    // When
    batchRequest.upsertWithRouting(INDEX, ID, entity, updateFields, routing);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.routing()).isEqualTo(routing);
    assertThat(update.action().doc()).isEqualTo(updateFields);
    assertThat(update.action().upsert()).isEqualTo(entity);
  }

  @Test
  void shouldUpsertWithScript() throws PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = mock(Script.class);
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    // When
    batchRequest.upsertWithScript(INDEX, ID, entity, script, params);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().script()).isEqualTo(scriptWithParameters);
    assertThat(update.action().upsert()).isEqualTo(entity);
  }

  @Test
  void shouldUpsertWithScriptAndRouting() throws PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");
    final String routing = "routing";

    final Script scriptWithParameters = mock(Script.class);
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    // When
    batchRequest.upsertWithScriptAndRouting(INDEX, ID, entity, script, params, routing);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.routing()).isEqualTo(routing);
    assertThat(update.action().script()).isEqualTo(scriptWithParameters);
    assertThat(update.action().upsert()).isEqualTo(entity);
  }

  @Test
  void shouldUpdateWithFields() throws PersistenceException {
    final Map<String, Object> updateFields = Map.of("id", "id2");

    // When
    batchRequest.update(INDEX, ID, updateFields);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().doc()).isEqualTo(updateFields);
  }

  @Test
  void shouldUpdateWithEntity() throws PersistenceException {
    // Given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // When
    batchRequest.update(INDEX, ID, entity);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().doc()).isEqualTo(entity);
  }

  @Test
  void shouldUpdateWithScript() throws PersistenceException {
    // Given
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = mock(Script.class);
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    // When
    batchRequest.updateWithScript(INDEX, ID, script, params);

    // Then
    final ArgumentCaptor<BulkOperation> captor = ArgumentCaptor.forClass(BulkOperation.class);
    verify(requestBuilder).operations(captor.capture());

    final var bulkOperation = captor.getValue();
    assertThat(bulkOperation.isUpdate()).isTrue();

    final var update = bulkOperation.update();
    assertThat(update.index()).isEqualTo(INDEX);
    assertThat(update.id()).isEqualTo(ID);
    assertThat(update.action().script()).isEqualTo(scriptWithParameters);
  }

  @Test
  void shouldFlushTheBulkRequestToElasticsearch() throws PersistenceException, IOException {
    // Given
    final BulkRequest bulkRequest = mock(BulkRequest.class);
    final BulkResponse bulkResponse = mock(BulkResponse.class);

    when(requestBuilder.build()).thenReturn(bulkRequest);
    when(elasticsearchClient.bulk(bulkRequest)).thenReturn(bulkResponse);

    // When
    batchRequest.execute();

    // Then
    verify(requestBuilder).build();
    verify(elasticsearchClient).bulk(bulkRequest);
  }

  @Test
  void shouldExecuteWithRefresh() throws PersistenceException, IOException {
    // Given
    final BulkRequest bulkRequest = mock(BulkRequest.class);
    final BulkResponse bulkResponse = mock(BulkResponse.class);

    when(requestBuilder.build()).thenReturn(bulkRequest);
    when(elasticsearchClient.bulk(bulkRequest)).thenReturn(bulkResponse);

    // When
    batchRequest.executeWithRefresh();

    // Then
    verify(requestBuilder).refresh(Refresh.True);
    verify(requestBuilder).build();
    verify(elasticsearchClient).bulk(bulkRequest);
  }

  @ParameterizedTest
  @ValueSource(classes = {IOException.class, ElasticsearchException.class})
  void shouldThrowPersistenceExceptionIfBulkRequestFails(final Class<? extends Throwable> throwable)
      throws IOException {
    // Given
    final BulkRequest bulkRequest = mock(BulkRequest.class);

    when(requestBuilder.build()).thenReturn(bulkRequest);
    when(elasticsearchClient.bulk(bulkRequest)).thenThrow(throwable);

    // When
    final ThrowingCallable callable = () -> batchRequest.execute();

    // Then
    assertThatThrownBy(callable).isInstanceOf(PersistenceException.class);
    verify(requestBuilder).build();
    verify(elasticsearchClient).bulk(bulkRequest);
  }

  @Test
  void shouldThrowPersistenceExceptionIfAResponseItemHasError() throws IOException {
    // Given
    final BulkRequest bulkRequest = mock(BulkRequest.class);

    final BulkResponseItem item = mock(BulkResponseItem.class);
    when(item.error()).thenReturn(new ErrorCause.Builder().reason("error").build());

    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(List.of(item));

    when(requestBuilder.build()).thenReturn(bulkRequest);
    when(elasticsearchClient.bulk(bulkRequest)).thenReturn(bulkResponse);

    // When
    final ThrowingCallable callable = () -> batchRequest.execute();

    // Then
    assertThatThrownBy(callable).isInstanceOf(PersistenceException.class);
    verify(requestBuilder).build();
    verify(elasticsearchClient).bulk(bulkRequest);
  }
}
