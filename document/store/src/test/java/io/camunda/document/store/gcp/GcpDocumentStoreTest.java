/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentReference;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Either.Left;
import io.camunda.zeebe.util.Either.Right;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
        new GcpDocumentStore(BUCKET_NAME, storage, mapper, Executors.newSingleThreadExecutor());
  }

  @Test
  void createDocumentBlobAlreadyExistsShouldFail() {
    // given
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest("documentId", inputStream, null);

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
        new DocumentCreationRequest("documentId", inputStream, null);

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
        new DocumentCreationRequest("documentId", inputStream, null);

    when(storage.get(BUCKET_NAME, "documentId")).thenReturn(null);

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
    assertThat(((Left<DocumentError, InputStream>) documentOperationResponse).value())
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
    assertThat(((Left<DocumentError, InputStream>) documentOperationResponse).value())
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
    assertThat(((Right<DocumentError, InputStream>) documentOperationResponse).value()).isNotNull();
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
}
