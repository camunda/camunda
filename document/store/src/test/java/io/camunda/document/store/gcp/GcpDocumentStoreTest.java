/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Either.Left;
import io.camunda.zeebe.util.Either.Right;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.DigestInputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GcpDocumentStoreTest {

  private static final String BUCKET_NAME = "camunda-saas-dev-test-document-storage";
  @Mock private Storage storage;
  private final ObjectMapper mapper = new ObjectMapper();
  private GcpDocumentStore gcpDocumentStore;

  @BeforeEach
  void init() {
    gcpDocumentStore =
        new GcpDocumentStore(
            BUCKET_NAME, null, storage, mapper, Executors.newSingleThreadExecutor());
  }

  @Test
  void createDocumentBlobAlreadyExistsShouldFail() {
    // given
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest(
            "documentId",
            inputStream,
            new DocumentMetadataModel(
                "application/json",
                "hello.json",
                OffsetDateTime.now(),
                10L,
                null,
                null,
                Map.of("key", "value")));

    when(storage.get(BUCKET_NAME, "documentId")).thenReturn(mock(Blob.class));

    // when
    final var documentReferenceResponse =
        gcpDocumentStore.createDocument(documentCreationRequest).join();

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Either.Left.class);
    assertThat(((Either.Left<DocumentError, DocumentReference>) documentReferenceResponse).value())
        .isInstanceOf(DocumentError.DocumentAlreadyExists.class);
  }

  @Test
  void createDocumentGcpThrowsExceptionShouldFail() throws IOException {
    // given
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest(
            "documentId",
            inputStream,
            new DocumentMetadataModel(
                "application/json",
                "hello.json",
                OffsetDateTime.now(),
                10L,
                null,
                null,
                Map.of("key", "value")));

    when(storage.get(BUCKET_NAME, "documentId")).thenReturn(null);
    when(storage.createFrom(BlobInfo.newBuilder(BUCKET_NAME, "documentId").build(), inputStream))
        .thenThrow(new RuntimeException("Failed to create document"));

    // when
    final var documentReferenceResponse =
        gcpDocumentStore.createDocument(documentCreationRequest).join();

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Left.class);
    assertThat(((Left<DocumentError, DocumentReference>) documentReferenceResponse).value())
        .isInstanceOf(UnknownDocumentError.class);
  }

  @Test
  void createDocumentShouldSucceed() {
    // given
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest(
            "documentId",
            inputStream,
            new DocumentMetadataModel(
                "application/json",
                "hello.json",
                OffsetDateTime.now(),
                10L,
                null,
                null,
                Map.of("key", "value")));

    final Blob mockBlob = mock(Blob.class);

    when(storage.get(BUCKET_NAME, "documentId")).thenReturn(null).thenReturn(mockBlob);

    // when
    final var documentReferenceResponse =
        gcpDocumentStore.createDocument(documentCreationRequest).join();

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentReference>) documentReferenceResponse).value())
        .isNotNull();
  }

  @Test
  void getDocumentBlobNotFoundShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(null);

    // when
    final var documentOperationResponse = gcpDocumentStore.getDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Left.class);
    assertThat(((Left<DocumentError, DocumentContent>) documentOperationResponse).value())
        .isInstanceOf(DocumentError.DocumentNotFound.class);
  }

  @Test
  void getDocumentGcpThrowsExceptionShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.get(BUCKET_NAME, documentId))
        .thenThrow(new RuntimeException("Failed to get document"));

    // when
    final var documentOperationResponse = gcpDocumentStore.getDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Left.class);
    assertThat(((Left<DocumentError, DocumentContent>) documentOperationResponse).value())
        .isInstanceOf(UnknownDocumentError.class);
  }

  @Test
  void getDocumentShouldSucceed() {
    // given
    final var documentId = "documentId";
    final var blob = mock(Blob.class);
    final var readChannel = mock(ReadChannel.class);

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(blob);
    when(blob.reader()).thenReturn(readChannel);

    // when
    final var documentOperationResponse = gcpDocumentStore.getDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentContent>) documentOperationResponse).value())
        .isNotNull();
  }

  @Test
  void deleteDocumentBlobNotFoundShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.delete(BUCKET_NAME, documentId)).thenReturn(false);

    // when
    final var documentOperationResponse = gcpDocumentStore.deleteDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Left.class);
    assertThat(((Left<DocumentError, Void>) documentOperationResponse).value())
        .isInstanceOf(DocumentError.DocumentNotFound.class);
  }

  @Test
  void deleteDocumentGcpThrowsExceptionShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.delete(BUCKET_NAME, documentId))
        .thenThrow(new RuntimeException("Failed to delete document"));

    // when
    final var documentOperationResponse = gcpDocumentStore.deleteDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Left.class);
    assertThat(((Left<DocumentError, Void>) documentOperationResponse).value())
        .isInstanceOf(UnknownDocumentError.class);
  }

  @Test
  void deleteDocumentShouldSucceed() {
    // given
    final var documentId = "documentId";

    when(storage.delete(BUCKET_NAME, documentId)).thenReturn(true);

    // when
    final var documentOperationResponse = gcpDocumentStore.deleteDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, Void>) documentOperationResponse).value()).isNull();
  }

  @Test
  void getDocumentLinkBlobNotFoundShouldFail() {
    // given
    final var documentId = "documentId";
    final var durationInSeconds = 60L;

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(null);

    // when
    final var documentOperationResponse =
        gcpDocumentStore.createLink(documentId, durationInSeconds).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Left.class);
    assertThat(((Left<DocumentError, DocumentLink>) documentOperationResponse).value())
        .isInstanceOf(DocumentError.DocumentNotFound.class);
  }

  @Test
  void getDocumentLinkGcpThrowsExceptionShouldFail() {
    // given
    final var documentId = "documentId";
    final var durationInSeconds = 60L;

    when(storage.get(BUCKET_NAME, documentId))
        .thenThrow(new RuntimeException("Failed to get document"));

    // when
    final var documentOperationResponse =
        gcpDocumentStore.createLink(documentId, durationInSeconds).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Left.class);
    assertThat(((Left<DocumentError, DocumentLink>) documentOperationResponse).value())
        .isInstanceOf(UnknownDocumentError.class);
  }

  @Test
  void getDocumentLinkShouldSucceed() throws MalformedURLException {
    // given
    final var documentId = "documentId";
    final var durationInMillis = 60 * 1000L;
    final var blob = mock(Blob.class);

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(blob);
    when(blob.signUrl(durationInMillis, TimeUnit.MILLISECONDS))
        .thenReturn(URI.create("http://localhost").toURL());

    // when
    final var documentOperationResponse =
        gcpDocumentStore.createLink(documentId, durationInMillis).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentLink>) documentOperationResponse).value().link())
        .isEqualTo("http://localhost");
  }

  @Test
  void createDocumentShouldUseCorrectPrefix() {
    // given
    gcpDocumentStore =
        new GcpDocumentStore(
            BUCKET_NAME, "prefix/", storage, mapper, Executors.newSingleThreadExecutor());
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest(
            "documentId",
            inputStream,
            new DocumentMetadataModel(
                "application/json",
                "hello.json",
                OffsetDateTime.now(),
                10L,
                null,
                null,
                Map.of("key", "value")));

    when(storage.get(BUCKET_NAME, "prefix/documentId")).thenReturn(null);

    // when
    final var documentReferenceResponse =
        gcpDocumentStore.createDocument(documentCreationRequest).join();

    // then
    assertThat(documentReferenceResponse).isNotNull();
    verify(storage).get(BUCKET_NAME, "prefix/documentId");
  }

  @Test
  void getDocumentShouldUseCorrectPrefix() {
    // given
    gcpDocumentStore =
        new GcpDocumentStore(
            BUCKET_NAME, "prefix/", storage, mapper, Executors.newSingleThreadExecutor());
    final var documentId = "documentId";
    final var blob = mock(Blob.class);
    final var readChannel = mock(ReadChannel.class);

    when(storage.get(BUCKET_NAME, "prefix/documentId")).thenReturn(blob);
    when(blob.reader()).thenReturn(readChannel);

    // when
    final var documentOperationResponse = gcpDocumentStore.getDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentContent>) documentOperationResponse).value())
        .isNotNull();
  }

  @Test
  void deleteDocumentShouldUseCorrectPrefix() {
    // given
    gcpDocumentStore =
        new GcpDocumentStore(
            BUCKET_NAME, "prefix/", storage, mapper, Executors.newSingleThreadExecutor());
    final var documentId = "documentId";

    when(storage.delete(BUCKET_NAME, "prefix/documentId")).thenReturn(true);

    // when
    final var documentOperationResponse = gcpDocumentStore.deleteDocument(documentId).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, Void>) documentOperationResponse).value()).isNull();
  }

  @Test
  void createLinkShouldUseCorrectPrefix() throws MalformedURLException {
    // given
    gcpDocumentStore =
        new GcpDocumentStore(
            BUCKET_NAME, "prefix/", storage, mapper, Executors.newSingleThreadExecutor());
    final var documentId = "documentId";
    final var durationInMillis = 60 * 1000L;
    final var blob = mock(Blob.class);

    when(storage.get(BUCKET_NAME, "prefix/documentId")).thenReturn(blob);
    when(blob.signUrl(durationInMillis, TimeUnit.MILLISECONDS))
        .thenReturn(URI.create("http://localhost").toURL());

    // when
    final var documentOperationResponse =
        gcpDocumentStore.createLink(documentId, durationInMillis).join();

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentLink>) documentOperationResponse).value().link())
        .isEqualTo("http://localhost");
  }

  @Test
  void shouldStoreProcessDefinitionIdIfPresent() {
    // given
    final var documentId = "documentId";
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest(
            documentId,
            inputStream,
            new DocumentMetadataModel(null, null, null, null, "processDefinitionId", null, null));

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(null);

    // when
    final var documentReferenceResponse =
        gcpDocumentStore.createDocument(documentCreationRequest).join();

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentReference>) documentReferenceResponse).value())
        .isNotNull();
    assertThat(
            ((Right<DocumentError, DocumentReference>) documentReferenceResponse)
                .value()
                .metadata()
                .processDefinitionId())
        .isEqualTo("processDefinitionId");
  }

  @Test
  void shouldStoreProcessInstanceKeyIfPresent() {
    // given
    final var documentId = "documentId";
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest(
            documentId,
            inputStream,
            new DocumentMetadataModel(null, null, null, null, null, 123L, null));

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(null);

    // when
    final var documentReferenceResponse =
        gcpDocumentStore.createDocument(documentCreationRequest).join();

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentReference>) documentReferenceResponse).value())
        .isNotNull();
    assertThat(
            ((Right<DocumentError, DocumentReference>) documentReferenceResponse)
                .value()
                .metadata()
                .processInstanceKey())
        .isEqualTo(123L);
  }

  @Test
  void shouldStoreCustomPropertiesIfPresent() throws IOException {
    // given
    final var documentId = "documentId";
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest(
            documentId,
            inputStream,
            new DocumentMetadataModel(
                null,
                null,
                null,
                null,
                "Def ID",
                123L,
                Map.of(
                    "String",
                    "StringValue",
                    "Int",
                    1,
                    "List",
                    List.of("str1", "str2"),
                    "Map",
                    Map.of("key1", "val1", "key2", "val2"))));
    when(storage.get(BUCKET_NAME, documentId)).thenReturn(null);
    when(storage.createFrom(any(), (InputStream) any()))
        .then(
            invocation -> {
              try (final var stream = invocation.getArgument(1, InputStream.class)) {
                stream.transferTo(OutputStream.nullOutputStream());
              }
              return null;
            });

    final var creationBlobCaptor = ArgumentCaptor.forClass(BlobInfo.class);
    final var updateBlobCaptor = ArgumentCaptor.forClass(BlobInfo.class);

    // when
    final var documentReferenceResponse =
        gcpDocumentStore.createDocument(documentCreationRequest).join();

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Right.class);
    assertThat(((Right<DocumentError, DocumentReference>) documentReferenceResponse).value())
        .isNotNull();
    assertThat(
            ((Right<DocumentError, DocumentReference>) documentReferenceResponse)
                .value()
                .metadata()
                .processInstanceKey())
        .isEqualTo(123L);
    verify(storage).createFrom(creationBlobCaptor.capture(), (DigestInputStream) any());
    verify(storage).update(updateBlobCaptor.capture());
    verifyNoMoreInteractions(storage);

    final var creationBlobMetadata = creationBlobCaptor.getValue().getMetadata();
    assertThat(creationBlobMetadata).hasSize(7);
    assertThat(creationBlobMetadata)
        .containsAllEntriesOf(
            Map.of(
                "String", "\"StringValue\"",
                "Int", "1",
                "List", "[\"str1\",\"str2\"]",
                "contentHash", "",
                "camunda.processDefinitionId", "Def ID",
                "camunda.processInstanceKey", "123"));

    final var deserialisedMap =
        mapper.readValue(
            creationBlobMetadata.get("Map"), new TypeReference<Map<String, Object>>() {});
    assertThat(deserialisedMap).isEqualTo(Map.of("key1", "val1", "key2", "val2"));

    final var updateBlobMetadata = updateBlobCaptor.getValue().getMetadata();
    assertThat(updateBlobMetadata).hasSize(7);
    assertThat(updateBlobMetadata)
        .containsAllEntriesOf(
            Map.of(
                "String", "\"StringValue\"",
                "Int", "1",
                "List", "[\"str1\",\"str2\"]",
                "contentHash", "ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73",
                "camunda.processDefinitionId", "Def ID",
                "camunda.processInstanceKey", "123"));

    final var deserialisedMap2 =
        mapper.readValue(
            creationBlobMetadata.get("Map"), new TypeReference<Map<String, Object>>() {});
    assertThat(deserialisedMap2).isEqualTo(Map.of("key1", "val1", "key2", "val2"));
  }

  @Test
  void validateSetupShouldHandleExceptionIfBucketDoesNotExist() {
    // given
    final var bucket = Mockito.mock(Bucket.class);
    when(storage.get(BUCKET_NAME)).thenReturn(bucket);
    when(bucket.exists()).thenReturn(false);

    // when
    assertThatNoException().isThrownBy(() -> gcpDocumentStore.validateSetup());

    // then
    verify(storage).get(BUCKET_NAME);
    verifyNoMoreInteractions(storage);
    verify(bucket).exists();
  }

  @Test
  void validateSetupShouldHandleExceptionIfUnknownExceptionIsThrown() {
    // given
    when(storage.get(BUCKET_NAME)).thenThrow(new RuntimeException());

    // when
    assertThatNoException().isThrownBy(() -> gcpDocumentStore.validateSetup());

    // then
    verify(storage).get(BUCKET_NAME);
    verifyNoMoreInteractions(storage);
  }
}
