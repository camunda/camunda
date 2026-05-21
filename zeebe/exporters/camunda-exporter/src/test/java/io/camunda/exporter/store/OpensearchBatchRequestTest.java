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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.entities.TestExporterEntity;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.utils.OpensearchScriptBuilder;
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
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.OperationType;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.util.BinaryData;

class OpensearchBatchRequestTest {

  private static final String ID = "id";
  private static final String INDEX = "index";
  private static final String INDEX_WITH_HANDLER = "indexWithHandler";
  private OpensearchBatchRequest batchRequest;
  private OpenSearchClient osClient;
  private OpensearchScriptBuilder scriptBuilder;
  private JacksonJsonpMapper jsonpMapper;

  @BeforeEach
  void setUp() throws IOException {
    osClient = mock(OpenSearchClient.class);
    final OpenSearchTransport transport = mock(OpenSearchTransport.class);
    jsonpMapper = new JacksonJsonpMapper();
    when(osClient._transport()).thenReturn(transport);
    when(transport.jsonpMapper()).thenReturn(jsonpMapper);
    scriptBuilder = mock(OpensearchScriptBuilder.class);
    batchRequest = new OpensearchBatchRequest(osClient, scriptBuilder);
    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(List.of());
    when(osClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
  }

  @Test
  void shouldAddABulkOperationWithSpecifiedIndexAndEntity()
      throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    batchRequest.add(INDEX, entity);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isIndex()).isTrue();
    assertThat(bulkOperation.index().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.index().id()).isEqualTo(ID);
    assertDocumentEquals(bulkOperation.index().document(), entity);
  }

  @Test
  void shouldAddWithRouting() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String routing = "routing";

    batchRequest.addWithRouting(INDEX, entity, routing);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isIndex()).isTrue();
    assertThat(bulkOperation.index().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.index().id()).isEqualTo(ID);
    assertThat(bulkOperation.index().routing()).isEqualTo(routing);
    assertDocumentEquals(bulkOperation.index().document(), entity);
  }

  @Test
  void shouldUpsertEntityWithUpdatedFields() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final Map<String, Object> updateFields = Map.of("id", "id2");

    batchRequest.upsert(INDEX, ID, entity, updateFields);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();
    assertThat(bulkOperation.update().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.update().id()).isEqualTo(ID);
    assertThat(bulkOperation.update().retryOnConflict())
        .isEqualTo(OpensearchBatchRequest.UPDATE_RETRY_COUNT);
  }

  @Test
  void shouldUpsertWithRouting() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final Map<String, Object> updateFields = Map.of("id", "id2");
    final String routing = "routing";

    batchRequest.upsertWithRouting(INDEX, ID, entity, updateFields, routing);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();
    assertThat(bulkOperation.update().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.update().id()).isEqualTo(ID);
    assertThat(bulkOperation.update().routing()).isEqualTo(routing);
  }

  @Test
  void shouldUpsertWithScript() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = realScript("ctx._source.id = params.id");
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    batchRequest.upsertWithScript(INDEX, ID, entity, script, params);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();
    assertThat(bulkOperation.update().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.update().id()).isEqualTo(ID);
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

    batchRequest.upsertWithScriptAndRouting(INDEX, ID, entity, script, params, routing);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();
    assertThat(bulkOperation.update().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.update().id()).isEqualTo(ID);
    assertThat(bulkOperation.update().routing()).isEqualTo(routing);
  }

  @Test
  void shouldUpdateWithFields() throws PersistenceException, IOException {
    // given
    final Map<String, Object> updateFields = Map.of("id", "id2");

    batchRequest.update(INDEX, ID, updateFields);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();
    assertThat(bulkOperation.update().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.update().id()).isEqualTo(ID);
  }

  @Test
  void shouldUpdateWithEntity() throws IOException, PersistenceException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    batchRequest.update(INDEX, ID, entity);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();
    assertThat(bulkOperation.update().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.update().id()).isEqualTo(ID);
  }

  @Test
  void shouldUpdateWithScript() throws PersistenceException, IOException {
    // given
    final String script = "script";
    final Map<String, Object> params = Map.of("id", "id2");

    final Script scriptWithParameters = realScript("ctx._source.id = params.id");
    when(scriptBuilder.getScriptWithParameters(script, params)).thenReturn(scriptWithParameters);

    batchRequest.updateWithScript(INDEX, ID, script, params);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isUpdate()).isTrue();
    assertThat(bulkOperation.update().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.update().id()).isEqualTo(ID);
  }

  @Test
  void shouldDeleteEntity() throws IOException, PersistenceException {
    // given
    batchRequest.delete(INDEX, ID);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isDelete()).isTrue();
    assertThat(bulkOperation.delete().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.delete().id()).isEqualTo(ID);
  }

  @Test
  void shouldDeleteEntityWithRouting() throws IOException, PersistenceException {
    // given
    final String routing = "routing";

    batchRequest.deleteWithRouting(INDEX, ID, routing);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(1);
    final var bulkOperation = operations.getFirst();
    assertThat(bulkOperation.isDelete()).isTrue();
    assertThat(bulkOperation.delete().index()).isEqualTo(INDEX);
    assertThat(bulkOperation.delete().id()).isEqualTo(ID);
    assertThat(bulkOperation.delete().routing()).isEqualTo(routing);
  }

  @Test
  void shouldExecuteWithMultipleOperationsInBatch() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);
    batchRequest.add(INDEX, entity);
    batchRequest.update(INDEX, ID, entity);
    // when
    batchRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());

    final List<BulkOperation> operations = captor.getValue().operations();
    assertThat(operations).hasSize(2);
  }

  @Test
  void shouldSplitIntoMultipleBulksWhenAccumulatedSizeExceedsCap()
      throws PersistenceException, IOException {
    // given
    final BatchRequest tinyCapRequest =
        new OpensearchBatchRequest(osClient, scriptBuilder).withMaxBytes(1L);
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    tinyCapRequest.add(INDEX, entity);
    tinyCapRequest.add(INDEX, entity);
    tinyCapRequest.add(INDEX, entity);
    // when
    tinyCapRequest.execute();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient, times(3)).bulk(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(req -> assertThat(req.operations()).hasSize(1));
  }

  @Test
  void shouldExecuteWithRefresh() throws PersistenceException, IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    batchRequest.add(INDEX, entity);
    // when
    batchRequest.executeWithRefresh();

    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    // then
    verify(osClient).bulk(captor.capture());
    final BulkRequest request = captor.getValue();
    assertThat(request.refresh()).isEqualTo(Refresh.True);
  }

  @ParameterizedTest
  @ValueSource(classes = {IOException.class, OpenSearchException.class})
  void shouldThrowPersistenceExceptionIfBulkRequestFails(final Class<? extends Throwable> throwable)
      throws IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    batchRequest.add(INDEX, entity);
    when(osClient.bulk(any(BulkRequest.class))).thenThrow(throwable);

    // when
    final ThrowingCallable callable = () -> batchRequest.execute();

    // then
    assertThatThrownBy(callable).isInstanceOf(PersistenceException.class);
    verify(osClient).bulk(any(BulkRequest.class));
  }

  @Test
  void shouldThrowPersistenceExceptionIfAResponseItemHasError() throws IOException {
    // given
    final TestExporterEntity entity = new TestExporterEntity().setId(ID);

    final BulkResponseItem item = mock(BulkResponseItem.class);
    when(item.id()).thenReturn("1");
    when(item.error())
        .thenReturn(new ErrorCause.Builder().type("string_error").reason("error").build());

    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(List.of(item));

    when(osClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

    batchRequest.add(INDEX, entity);
    // when
    final ThrowingCallable callable = () -> batchRequest.execute();

    // then
    assertThatThrownBy(callable).isInstanceOf(PersistenceException.class);
    verify(osClient).bulk(any(BulkRequest.class));
  }

  @Test
  void shouldUseCustomErrorHandlerIfProvided() throws IOException {
    // given
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
            new ErrorCause.Builder().type("document_missing_exception").reason("error").build());

    final BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.items()).thenReturn(List.of(item));

    when(osClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
    final BiConsumer<String, Error> errorHandler = mock(BiConsumer.class);

    final String message =
        String.format(
            "%s failed for type [%s] and id [%s]: %s",
            item.operationType(), item.index(), item.id(), item.error().reason());

    batchRequest.add(INDEX_WITH_HANDLER, entity);
    // when
    batchRequest.execute(errorHandler);

    // then
    verify(errorHandler)
        .accept(INDEX_WITH_HANDLER, new Error(message, item.error().type(), notFound));
  }

  @Test
  void chunkByBytesShouldReturnEmptyForNoOps() {
    // given
    // then
    assertThat(OpensearchBatchRequest.chunkByBytes(List.of(), 100L)).isEmpty();
  }

  @Test
  void chunkByBytesShouldReturnSingleChunkWhenUnderLimit() {
    // given
    final var ops =
        List.of(
            new OpensearchBatchRequest.SizedOperation(noopOp(), 10L),
            new OpensearchBatchRequest.SizedOperation(noopOp(), 20L));
    final var chunks = OpensearchBatchRequest.chunkByBytes(ops, 100L);
    // then
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(2);
  }

  @Test
  void chunkByBytesShouldKeepSingleOversizedOpInItsOwnChunk() {
    // given
    final var ops = List.of(new OpensearchBatchRequest.SizedOperation(noopOp(), 500L));
    final var chunks = OpensearchBatchRequest.chunkByBytes(ops, 100L);
    // then
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(1);
  }

  @Test
  void chunkByBytesShouldSplitWhenAdditionWouldExceedLimit() {
    // given
    final var ops =
        List.of(
            new OpensearchBatchRequest.SizedOperation(noopOp(), 60L),
            new OpensearchBatchRequest.SizedOperation(noopOp(), 60L),
            new OpensearchBatchRequest.SizedOperation(noopOp(), 60L));
    final var chunks = OpensearchBatchRequest.chunkByBytes(ops, 100L);
    // then
    assertThat(chunks).hasSize(3);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSize(1));
  }

  @Test
  void chunkByBytesShouldFitExactlyOnBoundary() {
    // given
    final var ops =
        List.of(
            new OpensearchBatchRequest.SizedOperation(noopOp(), 50L),
            new OpensearchBatchRequest.SizedOperation(noopOp(), 50L),
            new OpensearchBatchRequest.SizedOperation(noopOp(), 50L));
    final var chunks = OpensearchBatchRequest.chunkByBytes(ops, 100L);
    // then
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).hasSize(2);
    assertThat(chunks.get(1)).hasSize(1);
  }

  private void assertDocumentEquals(final Object actualDocument, final Object expectedDocument) {
    assertThat(actualDocument).isInstanceOf(BinaryData.class);
    final byte[] actualBytes = readAllBytes((BinaryData) actualDocument);
    final byte[] expectedBytes = readAllBytes(BinaryData.of(expectedDocument, jsonpMapper));
    assertThat(actualBytes).isEqualTo(expectedBytes);
  }

  private static byte[] readAllBytes(final BinaryData data) {
    try (var in = data.asInputStream()) {
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new AssertionError("failed to read BinaryData", e);
    }
  }

  private static Script realScript(final String source) {
    return Script.of(s -> s.inline(i -> i.source(source)));
  }

  private static BulkOperation noopOp() {
    return BulkOperation.of(b -> b.delete(d -> d.index("idx").id("id")));
  }
}
