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
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.util.BinaryData;
import io.camunda.exporter.entities.TestExporterEntity;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
  private ElasticsearchScriptBuilder scriptBuilder;
  private JacksonJsonpMapper jsonpMapper;

  @BeforeEach
  void setUp() throws IOException {
    elasticsearchClient = mock(ElasticsearchClient.class);
    jsonpMapper = new JacksonJsonpMapper();
    when(elasticsearchClient._jsonpMapper()).thenReturn(jsonpMapper);
    scriptBuilder = mock(ElasticsearchScriptBuilder.class);
    batchRequest = new ElasticsearchBatchRequest(elasticsearchClient, scriptBuilder);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(List.of());
    when(bulkResponse.errors()).thenReturn(false);
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
    final IndexOperation<?> index = bulkOperation.index();
    assertThat(index.index()).isEqualTo(INDEX);
    assertThat(index.id()).isEqualTo(ID);
    assertDocumentEquals(index.document(), entity);
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

    final IndexOperation<?> index = bulkOperation.index();
    assertThat(index.index()).isEqualTo(INDEX);
    assertThat(index.id()).isEqualTo(ID);
    assertThat(index.routing()).isEqualTo(routing);
    assertDocumentEquals(index.document(), entity);
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
    assertActionEquals(update.binaryAction(), a -> a.doc(updateFields).upsert(entity));
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
    assertActionEquals(update.binaryAction(), a -> a.doc(updateFields).upsert(entity));
  }

  @Test
  void shouldUpsertWithScript() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = realScript("ctx._source.id = params.id");
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
    assertActionEquals(update.binaryAction(), a -> a.script(scriptWithParameters).upsert(entity));
  }

  @Test
  void shouldUpsertWithScriptAndRouting() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");
    final String routing = "routing";

    final Script scriptWithParameters = realScript("ctx._source.id = params.id");
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
    assertActionEquals(update.binaryAction(), a -> a.script(scriptWithParameters).upsert(entity));
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
    assertActionEquals(update.binaryAction(), a -> a.doc(updateFields));
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
    assertActionEquals(update.binaryAction(), a -> a.doc(entity));
  }

  @Test
  void shouldUpdateWithScript() throws PersistenceException, IOException {
    // Given
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = realScript("ctx._source.id = params.id");
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
    assertActionEquals(update.binaryAction(), a -> a.script(scriptWithParameters));
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
  void shouldSplitIntoMultipleBulksWhenAccumulatedSizeExceedsCap()
      throws PersistenceException, IOException {
    // given - a tiny cap that any 2 operations will exceed, forcing one bulk per op
    final BatchRequest tinyCapRequest =
        new ElasticsearchBatchRequest(elasticsearchClient, scriptBuilder).withMaxBytes(1L);
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    // when
    tinyCapRequest.add(INDEX, entity);
    tinyCapRequest.add(INDEX, entity);
    tinyCapRequest.add(INDEX, entity);
    tinyCapRequest.execute();

    // then - three separate bulk calls, one operation each
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(elasticsearchClient, org.mockito.Mockito.times(3)).bulk(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(req -> assertThat(req.operations()).hasSize(1));
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
            "%s failed on index [%s] and id [%s]: %s",
            item.operationType(), item.index(), item.id(), item.error().reason());

    // When
    batchRequest.add(INDEX_WITH_HANDLER, entity);
    batchRequest.execute(errorHandler);

    // Then
    verify(errorHandler)
        .accept(INDEX_WITH_HANDLER, new Error(message, item.error().type(), notFound));
  }

  private void assertDocumentEquals(final Object actualDocument, final Object expectedDocument) {
    assertThat(actualDocument).isInstanceOf(BinaryData.class);
    final byte[] actualBytes = readAllBytes((BinaryData) actualDocument);
    final byte[] expectedBytes = readAllBytes(BinaryData.of(expectedDocument, jsonpMapper));
    assertThat(actualBytes).isEqualTo(expectedBytes);
  }

  private void assertActionEquals(
      final BinaryData actualAction,
      final Consumer<UpdateAction.Builder<Object, Object>> expectedActionBuilder) {
    assertThat(actualAction).as("update should carry pre-serialized binary action").isNotNull();
    final UpdateAction<Object, Object> expected =
        UpdateAction.of(
            b -> {
              expectedActionBuilder.accept(b);
              return b;
            });
    final byte[] actualBytes = readAllBytes(actualAction);
    final byte[] expectedBytes = readAllBytes(BinaryData.of(expected, jsonpMapper));
    assertThat(actualBytes).isEqualTo(expectedBytes);
  }

  private static byte[] readAllBytes(final BinaryData data) {
    try {
      return data.asInputStream().readAllBytes();
    } catch (final IOException e) {
      throw new AssertionError("failed to read BinaryData", e);
    }
  }

  private static Script realScript(final String source) {
    return Script.of(s -> s.source(source));
  }

  @Test
  void chunkByBytesShouldReturnEmptyForNoOps() {
    assertThat(ElasticsearchBatchRequest.chunkByBytes(List.of(), 100L)).isEmpty();
  }

  @Test
  void chunkByBytesShouldReturnSingleChunkWhenUnderLimit() {
    final var ops =
        List.of(
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 10L),
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 20L));
    final var chunks = ElasticsearchBatchRequest.chunkByBytes(ops, 100L);
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(2);
  }

  @Test
  void chunkByBytesShouldKeepSingleOversizedOpInItsOwnChunk() {
    final var ops = List.of(new ElasticsearchBatchRequest.SizedOperation(noopOp(), 500L));
    final var chunks = ElasticsearchBatchRequest.chunkByBytes(ops, 100L);
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(1);
  }

  @Test
  void chunkByBytesShouldSplitWhenAdditionWouldExceedLimit() {
    final var ops =
        List.of(
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 60L),
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 60L),
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 60L));
    final var chunks = ElasticsearchBatchRequest.chunkByBytes(ops, 100L);
    assertThat(chunks).hasSize(3);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSize(1));
  }

  @Test
  void chunkByBytesShouldFitExactlyOnBoundary() {
    final var ops =
        List.of(
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 50L),
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 50L),
            new ElasticsearchBatchRequest.SizedOperation(noopOp(), 50L));
    final var chunks = ElasticsearchBatchRequest.chunkByBytes(ops, 100L);
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).hasSize(2);
    assertThat(chunks.get(1)).hasSize(1);
  }

  private static BulkOperation noopOp() {
    return BulkOperation.of(b -> b.delete(d -> d.index("idx").id("id")));
  }
}
