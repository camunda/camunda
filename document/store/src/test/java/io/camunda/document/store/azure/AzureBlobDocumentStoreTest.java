/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.BlobInputStream;
import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentHashMismatch;
import io.camunda.document.api.DocumentError.DocumentNotFound;
import io.camunda.document.api.DocumentError.InvalidInput;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureBlobDocumentStoreTest {

  private static final String CONTAINER_NAME = "test-container";
  private static final String CONTAINER_PATH = "test/";

  @Mock private BlobContainerClient containerClient;
  @Mock private BlobServiceClient serviceClient;
  @Mock private BlobClient blobClient;

  private AzureBlobDocumentStore documentStore;

  @BeforeEach
  void setUp() {
    documentStore =
        new AzureBlobDocumentStore(
            CONTAINER_NAME,
            CONTAINER_PATH,
            containerClient,
            serviceClient,
            Executors.newSingleThreadExecutor());
  }

  @Test
  void shouldCreateDocumentSuccessfully() {
    // given
    final var documentId = "test-document-id";
    final var content = "test-content-random-bits\n.".getBytes();
    final var inputStream = new ByteArrayInputStream(content);

    final var metadata =
        new DocumentMetadataModel(
            "text/plain",
            "test-file.txt",
            null,
            (long) content.length,
            null,
            null,
            Collections.emptyMap());

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.exists()).thenReturn(false);
    doAnswer(
            invocation -> {
              final InputStream stream = invocation.getArgument(0);
              stream.transferTo(OutputStream.nullOutputStream());
              return null;
            })
        .when(blobClient)
        .upload(any(InputStream.class), anyLong(), eq(false));

    final var request = new DocumentCreationRequest(documentId, inputStream, metadata);

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get().documentId()).isEqualTo(documentId);
    assertThat(result.get().contentHash())
        .isEqualTo("3635e7279b883d6bfd13cfe4d8815cc01b70c678afc8d278c0a4c1b0afbb87a8");

    verify(blobClient).setMetadata(anyMap());
    verify(blobClient).setHttpHeaders(any());
  }

  @Test
  void shouldFailIfDocumentAlreadyExists() {
    // given
    final var documentId = "existing-document-id";
    final var inputStream = new ByteArrayInputStream(new byte[0]);
    final var metadata =
        new DocumentMetadataModel(null, null, null, 0L, null, null, Collections.emptyMap());
    final var request = new DocumentCreationRequest(documentId, inputStream, metadata);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.exists()).thenReturn(true);

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentAlreadyExists.class);
  }

  @Test
  void shouldFailForGeneralExceptionOnCreate() {
    // given
    final var documentId = "test-document-id";
    final var inputStream = new ByteArrayInputStream(new byte[0]);
    final var metadata =
        new DocumentMetadataModel(null, null, null, 0L, null, null, Collections.emptyMap());
    final var request = new DocumentCreationRequest(documentId, inputStream, metadata);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId))
        .thenThrow(new RuntimeException("Something went wrong"));

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(UnknownDocumentError.class);
  }

  @Test
  void shouldGetDocumentSuccessfully() {
    // given
    final var documentId = "test-document-id";
    final var blobInputStream = mock(BlobInputStream.class);
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(Collections.emptyMap());
    when(properties.getContentType()).thenReturn("text/plain");
    when(blobClient.openInputStream()).thenReturn(blobInputStream);

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertThat(result.isRight()).isTrue();
    final DocumentContent content = result.get();
    assertThat(content.contentType()).isEqualTo("text/plain");
  }

  @Test
  void shouldFailGetIfDocumentNotFound() {
    // given
    final var documentId = "test-document-id";
    final var exception = mock(BlobStorageException.class);
    when(exception.getStatusCode()).thenReturn(404);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenThrow(exception);

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentNotFound.class);
  }

  @Test
  void shouldFailGetIfDocumentExpired() {
    // given
    final var documentId = "test-document-id";
    final var expiresAt = OffsetDateTime.now().minus(Duration.ofDays(10)).toString();
    final var metadata = Map.of("expiresAt", expiresAt);
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(metadata);

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentNotFound.class);
  }

  @Test
  void shouldDeleteDocumentSuccessfully() {
    // given
    final var documentId = "test-document-id";

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    doNothing().when(blobClient).delete();

    // when
    final var result = documentStore.deleteDocument(documentId).join();

    // then
    assertThat(result.isRight()).isTrue();
    verify(blobClient).delete();
  }

  @Test
  void shouldFailDeleteIfDocumentNotFound() {
    // given
    final var documentId = "test-document-id";
    final var exception = mock(BlobStorageException.class);
    when(exception.getStatusCode()).thenReturn(404);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    doThrow(exception).when(blobClient).delete();

    // when
    final var result = documentStore.deleteDocument(documentId).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentNotFound.class);
  }

  @Test
  void shouldVerifyContentHashSuccessfully() {
    // given
    final var documentId = "test-document-id";
    final var contentHash = "randomhash";
    final var metadata = Map.of("contentHash", contentHash);
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(metadata);

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldFailVerifyForDifferentHash() {
    // given
    final var documentId = "test-document-id";
    final var contentHash = "contentHash";
    final var metadata = Map.of("contentHash", contentHash);
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(metadata);

    // when
    final var result = documentStore.verifyContentHash(documentId, "wrongHash").join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentHashMismatch.class);
  }

  @Test
  void shouldFailVerifyIfDocumentNotFound() {
    // given
    final var documentId = "test-document-id";
    final var exception = mock(BlobStorageException.class);
    when(exception.getStatusCode()).thenReturn(404);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenThrow(exception);

    // when
    final var result = documentStore.verifyContentHash(documentId, "hash").join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentNotFound.class);
  }

  @Test
  void shouldFailVerifyIfNoMetadata() {
    // given
    final var documentId = "test-document-id";
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(null);

    // when
    final var result = documentStore.verifyContentHash(documentId, "hash").join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(InvalidInput.class);
  }

  @Test
  void shouldFailVerifyIfNoContentHash() {
    // given
    final var documentId = "test-document-id";
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(Collections.emptyMap());

    // when
    final var result = documentStore.verifyContentHash(documentId, "hash").join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(InvalidInput.class);
  }

  @Test
  void shouldFailVerifyForGeneralException() {
    // given
    final var documentId = "test-document-id";

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId))
        .thenThrow(new RuntimeException());

    // when
    final var result = documentStore.verifyContentHash(documentId, "hash").join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(UnknownDocumentError.class);
  }

  @Test
  void shouldCreateLinkSuccessfully() {
    // given
    final var documentId = "test-document-id";
    final var durationInMillis = 10000L;
    final var metadata = Map.of("expiresAt", OffsetDateTime.now().plusDays(1).toString());
    final var properties = mock(BlobProperties.class);
    final var userDelegationKey = mock(UserDelegationKey.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(metadata);
    when(serviceClient.getUserDelegationKey(any(OffsetDateTime.class), any(OffsetDateTime.class)))
        .thenReturn(userDelegationKey);
    when(blobClient.generateUserDelegationSas(
            any(BlobServiceSasSignatureValues.class), any(UserDelegationKey.class)))
        .thenReturn("sastoken123");
    when(blobClient.getBlobUrl())
        .thenReturn("https://account.blob.core.windows.net/container/blob");

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertThat(result.isRight()).isTrue();
    final DocumentLink link = result.get();
    assertThat(link.link())
        .isEqualTo("https://account.blob.core.windows.net/container/blob?sastoken123");
    assertThat(link.expiresAt()).isNotNull();
  }

  @Test
  void shouldFallBackToServiceSasWhenUserDelegationFails() {
    // given
    final var documentId = "test-document-id";
    final var durationInMillis = 10000L;
    final var metadata = Map.of("expiresAt", OffsetDateTime.now().plusDays(1).toString());
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(metadata);
    when(serviceClient.getUserDelegationKey(any(OffsetDateTime.class), any(OffsetDateTime.class)))
        .thenThrow(new RuntimeException("User delegation not supported"));
    when(blobClient.generateSas(any(BlobServiceSasSignatureValues.class)))
        .thenReturn("servicesastoken");
    when(blobClient.getBlobUrl())
        .thenReturn("https://account.blob.core.windows.net/container/blob");

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertThat(result.isRight()).isTrue();
    final DocumentLink link = result.get();
    assertThat(link.link())
        .isEqualTo("https://account.blob.core.windows.net/container/blob?servicesastoken");
  }

  @Test
  void shouldFailCreateLinkForInvalidDuration() {
    // given
    final var documentId = "test-document-id";

    // when
    final var result = documentStore.createLink(documentId, -1L).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(InvalidInput.class);
    assertThat(((InvalidInput) result.getLeft()).message())
        .isEqualTo("Duration must be greater than 0");
  }

  @Test
  void shouldFailCreateLinkForDocumentNotFound() {
    // given
    final var documentId = "test-document-id";
    final var exception = mock(BlobStorageException.class);
    when(exception.getStatusCode()).thenReturn(404);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenThrow(exception);

    // when
    final var result = documentStore.createLink(documentId, 10000L).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentNotFound.class);
  }

  @Test
  void shouldFailCreateLinkForExpiredDocument() {
    // given
    final var documentId = "test-document-id";
    final var metadata = Map.of("expiresAt", OffsetDateTime.now().minusDays(1).toString());
    final var properties = mock(BlobProperties.class);

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId)).thenReturn(blobClient);
    when(blobClient.getProperties()).thenReturn(properties);
    when(properties.getMetadata()).thenReturn(metadata);

    // when
    final var result = documentStore.createLink(documentId, 10000L).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(DocumentNotFound.class);
  }

  @Test
  void shouldHandleGeneralExceptionOnCreateLink() {
    // given
    final var documentId = "test-document-id";

    when(containerClient.getBlobClient(CONTAINER_PATH + documentId))
        .thenThrow(new RuntimeException("Unexpected error"));

    // when
    final var result = documentStore.createLink(documentId, 10000L).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(UnknownDocumentError.class);
  }

  @Test
  void shouldValidateSetupSuccessfully() {
    // given
    when(containerClient.exists()).thenReturn(true);

    // when / then
    assertThatNoException().isThrownBy(() -> documentStore.validateSetup());
    verify(containerClient).exists();
  }

  @Test
  void shouldHandleExceptionInValidateSetup() {
    // given
    when(containerClient.exists()).thenThrow(new RuntimeException("Unexpected error"));

    // when / then
    assertThatNoException().isThrownBy(() -> documentStore.validateSetup());
    verify(containerClient).exists();
  }
}
