/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentOperationResponse;
import io.camunda.document.api.DocumentOperationResponse.DocumentErrorCode;
import io.camunda.document.api.DocumentOperationResponse.Failure;
import io.camunda.document.api.DocumentOperationResponse.Success;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
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
    gcpDocumentStore = new GcpDocumentStore(BUCKET_NAME, storage, mapper);
  }

  @Test
  void createDocumentBlobAlreadyExistsShouldFail() {
    // given
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest("documentId", inputStream, null);

    when(storage.get(BUCKET_NAME, "documentId")).thenReturn(mock(Blob.class));

    // when
    final var documentReferenceResponse = gcpDocumentStore.createDocument(documentCreationRequest);

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentReferenceResponse).errorCode())
        .isEqualTo(DocumentOperationResponse.DocumentErrorCode.DOCUMENT_ALREADY_EXISTS);
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
    final var documentReferenceResponse = gcpDocumentStore.createDocument(documentCreationRequest);

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentReferenceResponse).errorCode())
        .isEqualTo(DocumentErrorCode.UNKNOWN_ERROR);
  }

  @Test
  void createDocumentShouldSucceed() {
    // given
    final var inputStream = new ByteArrayInputStream("content".getBytes());
    final var documentCreationRequest =
        new DocumentCreationRequest("documentId", inputStream, null);

    when(storage.get(BUCKET_NAME, "documentId")).thenReturn(null);

    // when
    final var documentReferenceResponse = gcpDocumentStore.createDocument(documentCreationRequest);

    // then
    assertThat(documentReferenceResponse).isNotNull();
    assertThat(documentReferenceResponse).isInstanceOf(Success.class);
  }

  @Test
  void getDocumentBlobNotFoundShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(null);

    // when
    final var documentOperationResponse = gcpDocumentStore.getDocument(documentId);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentOperationResponse).errorCode())
        .isEqualTo(DocumentOperationResponse.DocumentErrorCode.DOCUMENT_NOT_FOUND);
  }

  @Test
  void getDocumentGcpThrowsExceptionShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.get(BUCKET_NAME, documentId))
        .thenThrow(new RuntimeException("Failed to get document"));

    // when
    final var documentOperationResponse = gcpDocumentStore.getDocument(documentId);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentOperationResponse).errorCode())
        .isEqualTo(DocumentErrorCode.UNKNOWN_ERROR);
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
    final var documentOperationResponse = gcpDocumentStore.getDocument(documentId);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Success.class);
  }

  @Test
  void deleteDocumentBlobNotFoundShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.delete(BUCKET_NAME, documentId)).thenReturn(false);

    // when
    final var documentOperationResponse = gcpDocumentStore.deleteDocument(documentId);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentOperationResponse).errorCode())
        .isEqualTo(DocumentOperationResponse.DocumentErrorCode.DOCUMENT_NOT_FOUND);
  }

  @Test
  void deleteDocumentGcpThrowsExceptionShouldFail() {
    // given
    final var documentId = "documentId";

    when(storage.delete(BUCKET_NAME, documentId))
        .thenThrow(new RuntimeException("Failed to delete document"));

    // when
    final var documentOperationResponse = gcpDocumentStore.deleteDocument(documentId);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentOperationResponse).errorCode())
        .isEqualTo(DocumentErrorCode.UNKNOWN_ERROR);
  }

  @Test
  void deleteDocumentShouldSucceed() {
    // given
    final var documentId = "documentId";

    when(storage.delete(BUCKET_NAME, documentId)).thenReturn(true);

    // when
    final var documentOperationResponse = gcpDocumentStore.deleteDocument(documentId);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Success.class);
  }

  @Test
  void getDocumentLinkBlobNotFoundShouldFail() {
    // given
    final var documentId = "documentId";
    final var durationInSeconds = 60L;

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(null);

    // when
    final var documentOperationResponse =
        gcpDocumentStore.createLink(documentId, durationInSeconds);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentOperationResponse).errorCode())
        .isEqualTo(DocumentOperationResponse.DocumentErrorCode.DOCUMENT_NOT_FOUND);
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
        gcpDocumentStore.createLink(documentId, durationInSeconds);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Failure.class);
    assertThat(((Failure<?>) documentOperationResponse).errorCode())
        .isEqualTo(DocumentErrorCode.UNKNOWN_ERROR);
  }

  @Test
  void getDocumentLinkShouldSucceed() throws MalformedURLException {
    // given
    final var documentId = "documentId";
    final var durationInSeconds = 60L;
    final var blob = mock(Blob.class);

    when(storage.get(BUCKET_NAME, documentId)).thenReturn(blob);
    when(blob.signUrl(durationInSeconds, TimeUnit.SECONDS))
        .thenReturn(URI.create("http://localhost").toURL());

    // when
    final var documentOperationResponse =
        gcpDocumentStore.createLink(documentId, durationInSeconds);

    // then
    assertThat(documentOperationResponse).isNotNull();
    assertThat(documentOperationResponse).isInstanceOf(Success.class);
    assertThat(((Success<DocumentLink>) documentOperationResponse).result().link())
        .isEqualTo("http://localhost");
  }
}
