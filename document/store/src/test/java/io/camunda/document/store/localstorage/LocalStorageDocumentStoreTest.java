/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.localstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.*;
import io.camunda.document.api.DocumentError.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class LocalStorageDocumentStoreTest {

  private Path storagePath;
  private LocalStorageDocumentStore documentStore;
  private final FileHandler fileHandler = new FileHandler();

  @BeforeEach
  void setUp() throws IOException {
    storagePath = Files.createTempDirectory(UUID.randomUUID().toString());
    documentStore =
        new LocalStorageDocumentStore(
            storagePath, fileHandler, new ObjectMapper(), Executors.newSingleThreadExecutor());
  }

  @AfterEach
  void cleanUp() throws IOException {
    deleteDirectoryRecursively(storagePath);
  }

  @Test
  void createDocumentShouldSucceed() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "some content I don't know".getBytes();
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
    final DocumentMetadataModel metadata = givenMetadata(content);
    final DocumentCreationRequest request =
        new DocumentCreationRequest(documentId, inputStream, metadata);

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isRight());
    final var documentReference = result.get();
    assertEquals(documentId, documentReference.documentId());
    assertEquals(
        "577bac45ad58fe5ee4e7f50d2d39a955d65baa825a61a970ba790d579f78e40e",
        documentReference.contentHash());
    assertTrue(Files.exists(storagePath.resolve(documentId)));
    assertTrue(
        Files.exists(storagePath.resolve(documentId + LocalStorageDocumentStore.METADATA_SUFFIX)));
  }

  @Test
  void createDocumentWithoutIdShouldReturnTheDocumentId() {
    // given
    final byte[] content = "test-content".getBytes();
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
    final DocumentMetadataModel metadata = givenMetadata(content);
    final DocumentCreationRequest request =
        new DocumentCreationRequest(null, inputStream, metadata);

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isRight());

    final var documentId = result.get().documentId();
    assertThat(documentId).isNotNull();
    assertTrue(Files.exists(storagePath.resolve(documentId)));
    assertTrue(
        Files.exists(storagePath.resolve(documentId + LocalStorageDocumentStore.METADATA_SUFFIX)));
  }

  @Test
  void createDocumentShouldFailIfDocumentAlreadyExists() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId); // create a document to simulate existing document

    final DocumentCreationRequest request =
        new DocumentCreationRequest(
            documentId, new ByteArrayInputStream(content), givenMetadata(content));

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentAlreadyExists.class, result.getLeft());
  }

  @Test
  void getDocumentShouldSucceed() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isRight());
    assertNotNull(result.get().inputStream());
  }

  @Test
  void getDocumentShouldFailIfDocumentNotFound() {
    // given
    final String documentId = UUID.randomUUID().toString();

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  @Test
  void deleteDocumentShouldSucceed() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    // when
    final var result = documentStore.deleteDocument(documentId).join();

    // then
    assertTrue(result.isRight());
    assertFalse(Files.exists(storagePath.resolve(documentId)));
    assertFalse(
        Files.exists(storagePath.resolve(documentId + LocalStorageDocumentStore.METADATA_SUFFIX)));
  }

  @Test
  void createLinkShouldFail() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final long durationInMillis = 10000L;

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(OperationNotSupported.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldSucceed() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    final String contentHash = "0a3666a0710c08aa6d0de92ce72beeb5b93124cce1bf3701c9d6cdeb543cb73e";

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertTrue(result.isRight());
  }

  @Test
  void verifyContentHashShouldFailIfHashDoesNotMatch() throws IOException {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    // when
    final var result = documentStore.verifyContentHash(documentId, "incorrect-hash").join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentHashMismatch.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldFailIfDocumentNotFound() {
    // given
    final String documentId = UUID.randomUUID().toString();

    // when
    final var result = documentStore.verifyContentHash(documentId, "contentHash").join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  @Test
  void validateSetupShouldHandleExceptionIfSecurityExceptionIsThrown() {
    try (final MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
      // given
      mockedFiles.when(() -> Files.exists(storagePath)).thenThrow(SecurityException.class);

      // when
      assertThatNoException().isThrownBy(() -> documentStore.validateSetup());

      // then
      mockedFiles.verify(() -> Files.exists(storagePath));
    }
  }

  private void deleteDirectoryRecursively(final Path directory) throws IOException {
    if (Files.exists(directory)) {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }

  private void createDocumentForTest(final byte[] content, final String documentId) {
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
    final DocumentMetadataModel metadata = givenMetadata(content);
    final DocumentCreationRequest request =
        new DocumentCreationRequest(documentId, inputStream, metadata);
    documentStore.createDocument(request).join();
  }

  private DocumentMetadataModel givenMetadata(final byte[] content) {
    return new DocumentMetadataModel(
        "text/plain", "test-file.txt", null, (long) content.length, null, null, null);
  }
}
